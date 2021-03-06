package com.arpnetworking.tsdaggregator.publishing;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.arpnetworking.tsdaggregator.AggregatedData;
import com.arpnetworking.tsdaggregator.statistics.SumStatistic;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintStream;

/**
 * Tests for the ConsolePublisher class
 *
 * @author barp
 */
public class ConsolePublisherTests {
    private PrintStream originalOut;
	private ByteArrayOutputStream byteArrayOutputStream;

	@Before
	public void setupOutput() {
		originalOut = System.out;
		byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArrayOutputStream);
		System.setOut(printStream);
	}

	@After
	public void restoreOutput() {
		System.setOut(originalOut);
	}

	@Test
	public void testConstruct() {
		@SuppressWarnings("UnusedAssignment") ConsolePublisher publisher = new ConsolePublisher();
	}

	@Test
	public void testOutputtingShowsSomething() {
		ConsolePublisher publisher = new ConsolePublisher();
		AggregatedData[] data = new AggregatedData[1];
		data[0] = new AggregatedData(new SumStatistic(), "service_name", "host", "set/view", 2332d, new DateTime(2013, 9, 20, 8, 15, 0, 0), Period.minutes(5), new Double[]{});
		publisher.recordAggregation(data);
		assertThat(byteArrayOutputStream.toString(), containsString("set/view"));
	}

	@Test
	public void testClosingDoesntChangeOutput() {
		ConsolePublisher publisher = new ConsolePublisher();
		AggregatedData[] data = new AggregatedData[1];
		data[0] = new AggregatedData(new SumStatistic(), "service_name", "host", "set/view", 2332d, new DateTime(2013, 9, 20, 8, 15, 0, 0), Period.minutes(5), new Double[]{});
		publisher.recordAggregation(data);
		String preClose = byteArrayOutputStream.toString();
		publisher.close();
		String postClose = byteArrayOutputStream.toString();
		assertThat(postClose, equalTo(preClose));
	}
}
