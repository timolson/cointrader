package org.cryptocoinpartners.util;

import org.la4j.LinearAlgebra;
import org.la4j.Matrix;
import org.la4j.matrix.DenseMatrix;

/**
 * Class describing a set of currencies and all the cross rates between them.
 */
public class KalmanFilter {

	private final int mStateCount; // n
	private final int mSensorCount; // m
	// state

	/**
	 * stateCount x 1
	 */
	private Matrix mState; // x, state estimate

	/**
	 * stateCount x stateCount
	 * <p>
	 * Symmetric.
	 * Down the diagonal of P, we find the variances of the elements of x.
	 * On the off diagonals, at P[i][j], we find the covariances of x[i] with x[j].
	 */
	private Matrix mStateCovariance; // Covariance matrix of x, process noise (w)

	// predict

	/**
	 * stateCount x stateCount
	 * <p>
	 * Kalman filters model a system over time.
	 * After each tick of time, we predict what the values of x are, and then we measure and do some computation.
	 * F is used in the update step. Here's how it works: For each value in x, we write an equation to update that value,
	 * a linear equation in all the variables in x. Then we can just read off the coefficients to make the matrix.
	 */
	private Matrix mUpdateMatrix; // F, State transition matrix.

	/**
	 * stateCount x stateCount
	 * <p>
	 * Error in the process, after each update this uncertainty is added.
	 */
	private Matrix mUpdateCovariance; // Q, Estimated error in process.

	/**
	 * stateCount x 1
	 * <p>
	 * The control input, the move vector.
	 * It's the change to x that we cause, or that we know is happening.
	 * Since we add it to x, it has dimension n. When the filter updates, it adds u to the new x.
	 * <p>
	 * External moves to the system.
	 */
	private Matrix mMoveVector; // u, Control vector

	// measurement

	/**
	 * sensorCount x 1
	 * <p>
	 * z: Measurement Vector, It's the outputs from the sensors.
	 */
	private Matrix mMeasurement;

	/**
	 * sensorCount x sensorCount
	 * <p>
	 * R, the variances and covariances of our sensor measurements.
	 * <p>
	 * The Kalman filter algorithm does not change R, because the process can't change our belief about the
	 * accuracy of our sensors--that's a property of the sensors themselves.
	 * We know the variance of our sensor either by testing it, or by reading the documentation that came with it,
	 * or something like that. Note that the covariances here are the covariances of the measurement error.
	 * A positive number means that if the first sensor is erroneously low, the second tends to be erroneously low,
	 * or if the first reads high, the second tends to read high; it doesn't mean that if the first sensor reports a
	 * high number the second will also report a high number
	 */
	private Matrix mMeasurementCovariance; // R, Covariance matrix of the measurement vector z

	/**
	 * sensorCount x stateCount
	 * <p>
	 * The matrix H tells us what sensor readings we'd get if x were the true state of affairs and our sensors were perfect.
	 * It's the matrix we use to extract the measurement from the data.
	 * If we multiply H times a perfectly correct x, we get a perfectly correct z.
	 */
	private Matrix mExtractionMatrix; // H, Observation matrix.

	// no inputs
	private Matrix mInnovation;
	private Matrix mInnovationCovariance;

	public KalmanFilter(int stateCount, int sensorCount) {
		mStateCount = stateCount;
		mSensorCount = sensorCount;
		mMoveVector = Matrix.zero(stateCount, 1);
	}

	private void step() {
		// prediction
		Matrix predictedState = mUpdateMatrix.multiply(mState).add(mMoveVector);
		Matrix predictedStateCovariance = mUpdateMatrix.multiply(mStateCovariance).multiply(mUpdateMatrix.transpose()).add(mUpdateCovariance);

		// observation
		mInnovation = mMeasurement.subtract(mExtractionMatrix.multiply(predictedState));
		mInnovationCovariance = mExtractionMatrix.multiply(predictedStateCovariance).multiply(mExtractionMatrix.transpose()).add(mMeasurementCovariance);

		// update
		Matrix kalmanGain = predictedStateCovariance.multiply(mExtractionMatrix.transpose())
				.multiply(mInnovationCovariance.withInverter(LinearAlgebra.InverterFactory.SMART).inverse());
		mState = predictedState.add(kalmanGain.multiply(mInnovation));

		int nRow = mStateCovariance.rows();
		mStateCovariance = DenseMatrix.identity(nRow).subtract(kalmanGain.multiply(mExtractionMatrix)).multiply(predictedStateCovariance);
	}

	public void step(Matrix measurement, Matrix move) {
		mMeasurement = measurement;
		mMoveVector = move;
		step();
	}

	public void step(Matrix measurement) {
		mMeasurement = measurement;
		step();
	}

	public Matrix getState() {
		return mState;
	}

	public Matrix getStateCovariance() {
		return mStateCovariance;
	}

	public Matrix getInnovation() {
		return mInnovation;
	}

	public Matrix getInnovationCovariance() {
		return mInnovationCovariance;
	}

	public void setState(Matrix state) {
		mState = state;
	}

	public void setStateCovariance(Matrix stateCovariance) {
		mStateCovariance = stateCovariance;
	}

	public void setUpdateMatrix(Matrix updateMatrix) {
		mUpdateMatrix = updateMatrix;
	}

	public void setUpdateCovariance(Matrix updateCovariance) {
		mUpdateCovariance = updateCovariance;
	}

	public void setMeasurementCovariance(Matrix measurementCovariance) {
		mMeasurementCovariance = measurementCovariance;
	}

	public void setExtractionMatrix(Matrix h) {
		this.mExtractionMatrix = h;
	}

	public Matrix getUpdateMatrix() {
		return mUpdateMatrix;
	}

	public Matrix getUpdateCovariance() {
		return mUpdateCovariance;
	}

	public Matrix getMeasurementCovariance() {
		return mMeasurementCovariance;
	}

	public Matrix getExtractionMatrix() {
		return mExtractionMatrix;
	}
}
