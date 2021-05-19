package org.cryptocoinpartners.esper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

public class LongestPostiveSequenceTest {

	// Replay replay = new Replay(false);

	//   Context context = Context.create(new EventTimeManager());
	/*
	 * protected Injector injector = Guice.createInjector(new AbstractModule() {
	 * @Override protected void configure() { bind(MockOrderService.class); } }); // @Before // public void setup() { // injector.injectMembers(this);
	 * // }
	 * @Inject BaseOrderService orderSerivce;
	 */
	@Test
	public final void test() {

		LongestPositiveSequence positiveSequenceChecker = new LongestPositiveSequence();
		LongestNegativeSequence negativeSequenceChecker = new LongestNegativeSequence();
		ArrayList<Double> sequence = new ArrayList<>(Arrays.asList(1d, 2d, 1d, 2d, 3d, 4d));
		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 6);
		assertEquals(negativeSequenceChecker.getValue(), 0);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

		sequence = new ArrayList<>(Arrays.asList(-1d, 2d, 1d, 2d, 3d, 4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 5);
		assertEquals(negativeSequenceChecker.getValue(), 1);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

		sequence = new ArrayList<>(Arrays.asList(1d, 2d, 1d, 2d, 3d, -4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 5);
		assertEquals(negativeSequenceChecker.getValue(), 1);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

		sequence = new ArrayList<>(Arrays.asList(-1d, 2d, 1d, 2d, 3d, -4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 4);
		assertEquals(negativeSequenceChecker.getValue(), 1);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

		sequence = new ArrayList<>(Arrays.asList(-1d, -2d, -1d, 2d, 3d, -4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 2);
		assertEquals(negativeSequenceChecker.getValue(), 3);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();
		sequence = new ArrayList<>(Arrays.asList(-1d, -2d, -1d, -2d, -3d, -4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 0);
		assertEquals(negativeSequenceChecker.getValue(), 6);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

		sequence = new ArrayList<>(Arrays.asList(-1d, -2d, -1d, -2d, -3d, 4d));

		for (Double i : sequence) {

			positiveSequenceChecker.enter(i);
			negativeSequenceChecker.enter(i);

		}
		assertEquals(positiveSequenceChecker.getValue(), 1);
		assertEquals(negativeSequenceChecker.getValue(), 5);
		negativeSequenceChecker.clear();
		positiveSequenceChecker.clear();

	}

}
