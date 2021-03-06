package com.arpnetworking.tsdaggregator.statistics;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

/**
 * Tests for the LastStatistic class
 *
 * @author barp
 */
public class LastTests {
	@Test
	public void testConstruction() {
		@SuppressWarnings("UnusedAssignment") LastStatistic stat = new LastStatistic();
	}

	@Test
	public void testGetName() {
		LastStatistic stat = new LastStatistic();
		assertThat(stat.getName(), equalTo("last"));
	}

	@Test
	public void testCalculate() {
		LastStatistic stat = new LastStatistic();
		Double[] vals = {12d, 18d, 5d};
		Double calculated = stat.calculate(vals);
		assertThat(calculated, equalTo(5d));
	}
}
