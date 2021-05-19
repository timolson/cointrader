package org.cryptocoinpartners.util;

import org.la4j.Matrix;

/**
 * Class describing a set of currencies and all the cross rates between them.
 */
public class Cointegration {
	double mDelta;
	double mR;
	KalmanFilter mFilter;
	int mNobs = 2;

	public Cointegration(double delta, double r) {
		mDelta = delta;
		mR = r;

		Matrix vw = Matrix.identity(mNobs).multiply(mDelta / (1 - delta));
		Matrix a = Matrix.identity(mNobs);

		Matrix x = Matrix.zero(mNobs, 1);

		mFilter = new KalmanFilter(mNobs, 1);
		mFilter.setUpdateMatrix(a);
		mFilter.setState(x);
		mFilter.setStateCovariance(Matrix.zero(mNobs, mNobs));
		mFilter.setUpdateCovariance(vw);
		mFilter.setMeasurementCovariance(Matrix.constant(1, 1, r));
	}

	public void step(double x, double y) {
		mFilter.setExtractionMatrix(Matrix.from1DArray(1, 2, new double[] { 1, x }));
		mFilter.step(Matrix.constant(1, 1, y));
	}

	public double getAlpha() {
		return mFilter.getState().getRow(0).get(0);
	}

	public double getBeta() {
		return mFilter.getState().getRow(1).get(0);
	}

	public double getVariance() {
		return mFilter.getInnovationCovariance().get(0, 0);
	}

	public double getError() {
		return mFilter.getInnovation().get(0, 0);
	}
}
