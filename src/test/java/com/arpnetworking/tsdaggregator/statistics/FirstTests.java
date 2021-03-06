package com.arpnetworking.tsdaggregator.statistics;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

/**
 * Tests for the FirstStatistic class
 *
 * @author barp
 */
public class FirstTests {
	@Test
	public void testConstruction() {
		@SuppressWarnings("UnusedAssignment") FirstStatistic stat = new FirstStatistic();
	}

	@Test
	public void testGetName() {
		FirstStatistic stat = new FirstStatistic();
		assertThat(stat.getName(), equalTo("first"));
	}

	@Test
	public void testCalculate() {
		FirstStatistic stat = new FirstStatistic();
		Double[] vals = {12d, 18d, 5d};
		Double calculated = stat.calculate(vals);
		assertThat(calculated, equalTo(12d));
	}
}
