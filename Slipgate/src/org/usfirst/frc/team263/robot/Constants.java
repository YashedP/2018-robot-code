package org.usfirst.frc.team263.robot;

public class Constants {
	// CAN Bus Motor IDs
	final static int kLeftMasterDrivePort = 1;
	final static int kLeftSlaveDrivePort = 0;
	final static int kRightMasterDrivePort = 3;
	final static int kRightSlaveDrivePort = 2;

	// PCM Module ID
	final static int kDriveSolenoidPort = 0;

	// Drivebase constants
	final static double[] kTurningConstant = { 0.3, 0.5 };
	final static double[] kDriveKp = { 0.5, 0.00 };
	final static double[] kDriveKi = { 0.001, 0.00 };
	final static double[] kDriveKd = { 5.0, 0.00 };
	final static double[] kDriveKf = { 0.00, 0.00 };
	final static int[] kDriveAccel = { 1000, 1000 };
	final static int[] kDriveCruiseVelocity = { 5000, 5000 };
	final static int[] kDriveError = { 10, 10 };
	final static int[] kDriveIZone = { 200, 200 };
	final static int[] kDriveRampRate = { 256, 256 };
	final static double[] kCubeSeekSpeed = { 0.25, 0.45 };
	final static double kDriveWheelLength = 23.5;

	// Rotational PID Constants
	final static double[] kDriveRKp = { 0.0025, 0.0028 };
	final static double[] kDriveRKi = { 0.000, 0.000 };
	final static double[] kDriveRKd = { 0.01, 0.000 };
	final static double[] kDriveRKf = { 0.00, 0.00 };
	final static double[] kDriveRStaticFr = { 0.094, 0.095 };
	final static double[] kDriveREpsilon = { 0.5, 0.5 };

	// Cube Intake constants
	final static int kLeftCubeWheel = 4;
	final static int kRightCubeWheel = 5;
	final static double kCubeWheelSpeed = 1;

	// Drivebase sensor/wheel constants
	final static double kUnitsPerRotationEnc = 4096.0 / 3.0;
	final static int kWheelRadiusInches = 3;
	final static double kWheelCircumference = 2 * kWheelRadiusInches * Math.PI;

	// DigitalInputs Port IDs
	final static int kCubeLeftLimitSwitch = 0;
	final static int kCubeRightLimitSwitch = 1;
}
