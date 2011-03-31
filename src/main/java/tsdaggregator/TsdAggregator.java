/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package tsdaggregator;

import java.io.*;
import java.util.*;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author brandarp
 */
public class TsdAggregator {
	
	static Logger _Logger = Logger.getLogger(TsdAggregator.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    	
    	Options options = new Options();
    	options.addOption("f", "file", true, "file to be parsed");
    	options.addOption("s", "service", true, "service name");
    	options.addOption("h", "host", true, "host the metrics were generated on");
    	CommandLineParser parser = new PosixParser();
    	CommandLine cl;
    	try {
			cl = parser.parse(options, args);
		} catch (ParseException e1) {
			printUsage(options);
			return;
		}
		
		PropertyConfigurator.configure("log4j.properties");
		
		
		if (!cl.hasOption("f")) {
			System.err.println("no file found, must specify file on the command line");
			printUsage(options);
			return;
		}
		
		if (!cl.hasOption("s")) {
			System.err.println("service name must be specified");
			printUsage(options);
			return;
		}
		
		if (!cl.hasOption("h")) {
			System.err.println("host name must be specified");
			printUsage(options);
			return;
		}
		
		_Logger.info("using file " + cl.getOptionValue("f"));
		_Logger.info("using hostname " + cl.getOptionValue("h"));
		_Logger.info("using servicename " + cl.getOptionValue("s"));
    	
    	
        HashMap<String, TSData> aggregations = new HashMap<String, TSData>();
        try {
			FileReader fileReader = new FileReader(args[0]);
			BufferedReader reader = new BufferedReader(fileReader);
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
				LineData data = new LineData();
				data.parseLogLine(line);
				for (Map.Entry<String, Double> entry : data.getVariables().entrySet()) {
					TSData tsdata = aggregations.get(entry.getKey());
					tsdata.addMetric(entry.getValue(), data.getTime());
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public static void printUsage(Options options)
    {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("tsdaggregator", options, true);
    }
}
