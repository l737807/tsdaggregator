/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.arpnetworking.tsdaggregator;

import com.arpnetworking.tsdaggregator.publishing.*;
import com.arpnetworking.tsdaggregator.statistics.Statistic;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.io.input.Tailer;
import org.apache.log4j.Logger;
import org.joda.time.Period;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * @author barp
 */
public class TsdAggregator {

    private static final Logger _Logger = Logger.getLogger(TsdAggregator.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                _Logger.error("Unhandled exception!", throwable);
            }
        });

        CommandLineParser parser = new CommandLineParser(new DefaultHostResolver());
        Configuration commandLineConfig;
        try {
            commandLineConfig = parser.parse(args);
        } catch (ConfigException e) {
            System.err.println("error parsing options: " + e.getMessage());
            parser.printUsage(System.err);
            return;
        }

        List<Configuration> configurations = new ArrayList<>();
        configurations.add(commandLineConfig);

        List<String> configFiles = commandLineConfig.getConfigFiles();
        ConfigFileParser configFileParser = new ConfigFileParser(new DefaultHostResolver());
        for (String configFile : configFiles) {
            try {
                Configuration fileConfig = configFileParser.parse(configFile);
                configurations.add(fileConfig);
            } catch (ConfigException e) {
                _Logger.warn("Could not parse config file " + configFile, e);
            }
        }

        for (final Configuration config : configurations) {
            Thread runnerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    startConfiguration(config);
                }
            });

            runnerThread.start();
        }
    }

    public static void startConfiguration(final Configuration config) {
        if (!config.isValid()) {
            return;
        }
        Class<? extends LogParser> parserClass = config.getParserClass();
        LogParser logParser;
        try {
            logParser = parserClass.newInstance();
        } catch (@Nonnull InstantiationException | IllegalAccessException e) {
            _Logger.error("Could not instantiate parser class", e);
            return;
        }

        Set<Period> periods = config.getPeriods();
        Set<Statistic> counterStatsClasses = config.getCounterStatistics();
        Set<Statistic> timerStatsClasses = config.getTimerStatistics();
        Set<Statistic> gaugeStatsClasses = config.getGaugeStatistics();

        Pattern filter = config.getFilterPattern();

        Boolean tailFile = config.shouldTailFiles();

        List<String> fileNames = config.getFiles();
        String hostName = config.getHostName();

        String cluster = config.getClusterName();
        String serviceName = config.getServiceName();
        String metricsUri = config.getMetricsUri();
        String monitordUri = config.getMonitordAddress();
        String outputFile = config.getOutputFile();
        Boolean outputRRD = config.shouldUseRRD();
        String remetUri = config.getRemetAddress();


        _Logger.info("using files ");
        for (String file : fileNames) {
            _Logger.info("    " + file);
        }
        _Logger.info("using cluster " + cluster);
        _Logger.info("using hostname " + hostName);
        _Logger.info("using servicename " + serviceName);
        _Logger.info("using uri " + metricsUri);
        _Logger.info("using remetURI uri " + remetUri);
        _Logger.info("using monitord uri " + monitordUri);
        _Logger.info("using output file " + outputFile);
        _Logger.info("using filter (" + filter.pattern() + ")");
        _Logger.info("using counter stats " + counterStatsClasses.toString());
        _Logger.info("using timer stats " + timerStatsClasses.toString());
        _Logger.info("using gauge stats " + gaugeStatsClasses.toString());
        if (outputRRD) {
            _Logger.info("outputting rrd files");
        }

        PlatformManager platformManager = PlatformLocator.factory.createPlatformManager();
        if (config.shouldStartClusterAggServer()) {
            int port = config.getClusterAggServerPort();
            URL url = Resources.getResource("mod.json");
            try {
                String text = Resources.toString(url, Charsets.UTF_8);
                JsonObject conf = new JsonObject(text);
                conf.putNumber("port", port);

                URL[] urls = new URL[1];
                String urlString = url.toString();
                urlString = urlString.substring(4, urlString.indexOf("!"));
                _Logger.info(url);
                _Logger.info(urlString);
                urls[0] = new URL(urlString);

                platformManager.deployModuleFromClasspath("com.arpnetworking~agg-server~1.0", conf, 1, urls,
                        new AsyncResultHandler<String>() {
                            @Override
                            public void handle(final AsyncResult<String> event) {
                                if (event.succeeded()) {
                                    _Logger.info("Aggregation server started, deployment id " + event.result());
                                } else {
                                    _Logger.error("Error starting aggregation server", event.cause());
                                }
                    }
                    });
            } catch (IOException e) {
                _Logger.error("unable to read module config for aggregation server", e);
                return;
            }
        }

        AggregationPublisher publisher = getPublisher(config, platformManager);

        LineProcessor processor =
                new LineProcessor(logParser, timerStatsClasses, counterStatsClasses, gaugeStatsClasses, hostName,
                        serviceName, periods, publisher);

        ArrayList<String> files = getFileList(filter, fileNames);
        for (String f : files) {
            try {
                _Logger.info("Reading file " + f);
                if (tailFile) {
                    File fileHandle = new File(f);
                    LogTailerListener tailListener = new LogTailerListener(processor);
                    Tailer.create(fileHandle, tailListener, 500L, false);
                } else {
                    //check the first 4 bytes of the file for utf markers
                    FileInputStream fis = null;
                    BufferedReader reader = null;
                    try {
                        fis = new FileInputStream(f);
                        byte[] header = new byte[4];
                        if (fis.read(header) < 4) {
                            //If there are less than 4 bytes, we should move on
                            continue;
                        }
                        Charset encoding = Charsets.UTF_8;
                        if (header[0] == -1 && header[1] == -2) {
                            _Logger.info("Detected UTF-16 encoding");
                            encoding = Charsets.UTF_16;
                        }

                        InputStreamReader fileReader = new InputStreamReader(new FileInputStream(f), encoding);
                        reader = new BufferedReader(fileReader);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            processor.invoke(line);
                        }
                    } finally {
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (Exception ignored) { }
                        }

                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Exception ignored) { }
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        processor.closeAggregations();

        if (tailFile) {
            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        publisher.close();
    }

    private static ArrayList<String> getFileList(final Pattern filter, final List<String> files) {
        ArrayList<String> fileList = new ArrayList<>();
        for (String fileName : files) {
            File file = new File(fileName);
            if (file.isFile()) {
                fileList.add(fileName);
            } else if (file.isDirectory()) {
                _Logger.info("File given is a directory, will recursively process");
                findFilesRecursive(file, fileList, filter);
            }
        }
        return fileList;
    }

    private static AggregationPublisher getPublisher(final Configuration config,
                                                     final PlatformManager platformManager) {
        MultiPublisher listener = new MultiPublisher();

        String hostName = config.getHostName();
        String cluster = config.getClusterName();
        String metricsUri = config.getMetricsUri();
        String monitordUri = config.getMonitordAddress();
        String outputFile = config.getOutputFile();
        boolean outputRRD = config.shouldUseRRD();
        boolean outputMonitord = config.shouldUseMonitord();
        boolean outputRemet = config.useRemet();
        String remetUri = config.getRemetAddress();
        boolean outputUpstreamAgg = config.shouldUseUpstreamAgg();
        String upstreamAggHost = config.getClusterAggHost();
        if (!metricsUri.equals("")) {
            _Logger.info("Adding buffered HTTP POST listener");
            AggregationPublisher httpListener = new HttpPostPublisher(metricsUri);
            listener.addListener(new BufferingPublisher(httpListener, 50));
        }


        if (outputRemet) {
            _Logger.info("Adding remet listener");
            AggregationPublisher httpListener = new HttpPostPublisher(remetUri);
            //we don't want to buffer remet responses
            listener.addListener(httpListener);
        }

        if (outputMonitord) {
            _Logger.info("Adding monitord listener");
            AggregationPublisher monitordListener = new MonitordPublisher(monitordUri, cluster, hostName);
            listener.addListener(monitordListener);
        }

        if (!outputFile.equals("")) {
            _Logger.info("Adding file listener");
            AggregationPublisher fileListener = new FilePublisher(outputFile);
            listener.addListener(fileListener);
        }

        if (outputRRD) {
            _Logger.info("Adding RRD listener");
            listener.addListener(new RRDClusterPublisher());
        }

        if (outputUpstreamAgg) {
            _Logger.info("Adding upstream aggregation listener");
            listener.addListener(new ClusterAggregationPublisher(upstreamAggHost, hostName, cluster));
        }
        return listener;
    }

    private static void findFilesRecursive(@Nonnull File dir, @Nonnull ArrayList<String> files,
                                           @Nonnull Pattern filter) {
        String[] list = dir.list();
        Arrays.sort(list);
        for (String f : list) {
            File entry = new File(dir, f);
            if (entry.isFile()) {
                Matcher m = filter.matcher(entry.getPath());
                if (m.find()) {
                    files.add(entry.getAbsolutePath());
                }
            } else if (entry.isDirectory()) {
                findFilesRecursive(entry, files, filter);
            }
        }
    }
}
