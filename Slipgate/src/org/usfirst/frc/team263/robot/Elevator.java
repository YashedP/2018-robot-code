package org.usfirst.frc.team263.robot;

import java.util.Arrays;

import org.usfirst.frc.team263.robot.Enums.ElevatorPosition;
import org.usfirst.frc.team263.robot.Enums.LEDMode;

import com.ctre.phoenix.motion.MotionProfileStatus;
import com.ctre.phoenix.motion.SetValueMotionProfile;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.LimitSwitchNormal;
import com.ctre.phoenix.motorcontrol.LimitSwitchSource;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

import edu.wpi.first.wpilibj.DigitalOutput;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.GenericHID.Hand;
import edu.wpi.first.wpilibj.Notifier;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;

/**
 * Controller for elevator system on robot.
 * 
 * @version 0.0.2
 * @author Rohan Bapat
 * @since 01-29-2018
 */
public class Elevator {

	private static Elevator instance = new Elevator();
	private MotionProfileStatus profileStatus;
	private TalonSRX mElevatorTalon;
	private boolean upPressed, downPressed, L3Pressed, nudgeUpPressed, nudgeDownPressed, yPressed, ltPressed;
	private boolean profileReady, profileRunning;
	private Notifier bufferProcessor;
	private Notifier streamerThread;
	private DoubleSolenoid tiltSolenoid;
	private ElevatorPosition targetLevel;
	private ElevatorPosition elevatorLevel;
	private int currentCount;
	private ElevatorProfile prof;
	private int[] encoderLevels;
	private DigitalOutput override;

	/**
	 * Constructor for Elevator class.
	 */
	private Elevator() {
		// configure TalonSRX for closed-loop control
		mElevatorTalon = new TalonSRX(Constants.kElevatorTalon);
		mElevatorTalon.setNeutralMode(NeutralMode.Brake);
		mElevatorTalon.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, 0, 0);
		mElevatorTalon.setSensorPhase(true);
		mElevatorTalon.config_kP(0, Constants.kElevatorKp, 0);
		mElevatorTalon.config_kI(0, Constants.kElevatorKi, 0);
		mElevatorTalon.config_kD(0, Constants.kElevatorKd, 0);
		mElevatorTalon.config_kF(0, Constants.kElevatorKf, 0);
		mElevatorTalon.setSelectedSensorPosition(Constants.kInitialCount, 0, 0);
		mElevatorTalon.configForwardLimitSwitchSource(LimitSwitchSource.FeedbackConnector,
				LimitSwitchNormal.NormallyOpen, 0);
		mElevatorTalon.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector,
				LimitSwitchNormal.NormallyOpen, 0);
		profileStatus = new MotionProfileStatus();
		// mElevatorTalon.overrideLimitSwitchesEnable(true);

		// buffer and stream processor threads
		bufferProcessor = new Notifier(new BufferProcessor());
		streamerThread = new Notifier(new ProfileStreamer());
		streamerThread.startPeriodic(0.005);

		// Solenoid for elevator tilt actuation
		tiltSolenoid = new DoubleSolenoid(0, Constants.kElevatorSolFwd, Constants.kElevatorSolRev);

		// Wrapper for profiling points
		prof = new ElevatorProfile();

		// elevator and encoder levels
		targetLevel = ElevatorPosition.kInitial;
		elevatorLevel = ElevatorPosition.kInitial;

		// TODO: replace with actual values taken from open-loop control on
		// elevator { reserved, ground, vault, switch, minScale, midScale, maxScale }
		encoderLevels = new int[] { -1, 0, 460, 3400, 8000, 10000, 11500 };
		currentCount = Constants.kInitialCount;

		// booleans for button presses and profile availablility
		upPressed = false;
		downPressed = false;
		L3Pressed = false;
		profileReady = false;
		profileRunning = false;
		nudgeUpPressed = false;
		nudgeDownPressed = false;
		yPressed = false;

		// Manual override for kill switch
		override = new DigitalOutput(Constants.kOverride);

		clearEverything();
	}

	/**
	 * Gets instance of singleton Elevator.
	 * 
	 * @return Gets the one instance of Elevator.
	 */
	public static Elevator getInstance() {
		return instance;
	}

	/**
	 * Method to drive robot given controller of secondary driver.
	 * 
	 * @param controller
	 *            Secondary driver's controller.
	 */
	public void drive(XboxController controller) {
		if (deadband(controller.getTriggerAxis(Hand.kLeft), 0.5) != 0) {
			clearEverything();
			mElevatorTalon.set(ControlMode.PercentOutput, deadband(-controller.getY(Hand.kLeft), 0.1));
		}
		if (deadband(controller.getTriggerAxis(Hand.kLeft), 0.5) == 0 && ltPressed) {
			mElevatorTalon.set(ControlMode.PercentOutput, 0.05);
		}
		if (controller.getBumper(Hand.kRight) && !upPressed) {
			targetLevel = ElevatorPosition.values()[Math.min(targetLevel.ordinal() + 1, encoderLevels.length - 1)];
			if(targetLevel.ordinal() == encoderLevels.length-1) {
				new ControllerRumble(controller, 1).run();
			}
		} else if (controller.getBumper(Hand.kLeft) && !downPressed) {
			targetLevel = ElevatorPosition.values()[Math.max(targetLevel.ordinal() - 1, 1)];
		} else if (controller.getBumper(Hand.kLeft) && controller.getBumper(Hand.kRight)) {
			clearEverything();
			targetLevel = elevatorLevel;
		} else if (controller.getPOV() == 0 && !nudgeUpPressed) {
			encoderLevels[elevatorLevel.ordinal()] += 20;
			executeHead();
		} else if (controller.getPOV() == 180 && !nudgeDownPressed) {
			encoderLevels[elevatorLevel.ordinal()] -= 20;
			executeHead();
		} else if (controller.getStickButton(Hand.kLeft) && !L3Pressed) {
			executeHead();
		}

		if (controller.getStartButton()) {
			override.set(true);
			LEDStrip.sendColor(LEDMode.eBlink);
		}

		if (mElevatorTalon.getSensorCollection().isRevLimitSwitchClosed()) {
			mElevatorTalon.setSelectedSensorPosition(0, 0, 0);
		}

		if (controller.getYButton() && !yPressed) {
			tiltSolenoid.set(tiltSolenoid.get() == Value.kForward ? Value.kReverse : Value.kForward);
		}

		talonCheck();

		upPressed = controller.getBumper(Hand.kRight);
		downPressed = controller.getBumper(Hand.kLeft);
		L3Pressed = controller.getStickButton(Hand.kLeft);
		nudgeUpPressed = controller.getPOV() == 0;
		nudgeDownPressed = controller.getPOV() == 180;
		yPressed = controller.getYButton();
		ltPressed = deadband(controller.getTriggerAxis(Hand.kLeft), 0.5) != 0;
	}

	/**
	 * Wrapper method to send elevator to ground.
	 */
	public void toGround() {
		toPosition(ElevatorPosition.kGround);
	}

	/**
	 * Wrapper method to send elevator to scale height.
	 */
	public void toScale() {
		toPosition(ElevatorPosition.kMiddleScale);
	}

	/**
	 * Wrapper method to send elevator to switch height.
	 */
	public void toSwitch() {
		toPosition(ElevatorPosition.kSwitch);
	}

	/**
	 * Wrapper method to send scale to given level.
	 * 
	 * @param level
	 *            level to send elevator to.
	 */
	public void toPosition(ElevatorPosition position) {
		targetLevel = position;

	}

	/**
	 * @return If a motion profile is currently running.
	 */
	public boolean isFinished() {
		return !profileRunning && atTarget();
	}

	/**
	 * Executes the command at the head of the trajectory queue.
	 * 
	 * Not the Maximilien Robespierre style though.
	 */
	private void executeHead() {
		System.out.println("Current: " + elevatorLevel.ordinal() + "-" + mElevatorTalon.getSelectedSensorPosition(0)
				+ ", Target: " + targetLevel.ordinal() + "-" + encoderLevels[targetLevel.ordinal()] + ", delta: "
				+ getDelta(elevatorLevel) + ", btm: " + profileStatus.btmBufferCnt + ", " + profileReady + " "
				+ profileRunning);
		if (!atTarget() && profileReady) {
			System.out.println("ExecuteHead");
			profileRunning = true;
		} else {
			System.out.println("Cant ExecuteHead: " + !atTarget() + " " + profileReady);
			new Notifier(new Runnable() {
				@Override
				public void run() {
					profileRunning = (!atTarget() && profileReady);
				}
			}).startSingle(0.05); // TODO: TUNE THIS VALUE
		}
	}

	/**
	 * Checks on talon with the profile it retrieved. Handles buffer sizes and
	 * buffer underflows.
	 */
	private void talonCheck() {
		synchronized (this) {

			currentCount = mElevatorTalon.getSelectedSensorPosition(0);
			mElevatorTalon.getMotionProfileStatus(profileStatus);

			if (profileStatus.hasUnderrun) {
				mElevatorTalon.clearMotionProfileHasUnderrun(0);
			}

			// if (profileStatus.btmBufferCnt > Constants.kMinBufferSize) {
			// profileReady = true;

			// }

			if (profileStatus.activePointValid && profileStatus.isLast) {
				profileRunning = false;
				profileReady = false;
				mElevatorTalon.set(ControlMode.MotionProfile, SetValueMotionProfile.Hold.value);
			}

			if (profileReady && profileRunning) {
				mElevatorTalon.set(ControlMode.MotionProfile, SetValueMotionProfile.Enable.value);
			}

			if (getDelta(elevatorLevel) > Constants.kElevatorThreshhold) {
				for (ElevatorPosition el : ElevatorPosition.values()) {
					if (getDelta(el) < Constants.kElevatorThreshhold) {
						elevatorLevel = el;
					}
				}
			}

			/*
			 * TODO: add data streamer that sends interpretable info to driver station,
			 * either with ShuffleBoard, SmartDashboard, or UDP Sockets
			 */
		}
	}

	/**
	 * Applies a deadband to a given value.
	 * 
	 * @param value
	 *            Value to apply deadband to.
	 * @param deadband
	 *            Minimum value.
	 * @return value if value is greater than deadband, 0 otherwise.
	 */
	private double deadband(double value, double deadband) {
		return Math.abs(value) > deadband ? value : 0.0;
	}

	/**
	 * Clears everything in queues/arrays, terminates threads, and clears talon.
	 * buffer
	 */
	private void clearEverything() {
		bufferProcessor.stop();
		prof.reset();
		mElevatorTalon.clearMotionProfileTrajectories();
		mElevatorTalon.clearMotionProfileHasUnderrun(0);
	}

	/**
	 * Checks whether or not elevator is at target state given position enum and
	 * position in encoder ticks
	 * 
	 * @return whether or not elevator is at target
	 */
	private boolean atTarget() {
		return elevatorLevel == targetLevel && getDelta(elevatorLevel) < Constants.kElevatorThreshhold;
	}

	/**
	 * Gets distance between given elevator position and actual elevator position in
	 * natural units
	 * 
	 * @param position
	 *            elevator position to find delta
	 * 
	 * @return Gets distance between assumed elevator position and actual elevator
	 *         position in natural units
	 */
	private int getDelta(ElevatorPosition position) {
		return Math.abs(currentCount - encoderLevels[position.ordinal()]);
	}

	/**
	 * Runnable used in notifier to move top-level buffer data to the bottom
	 * firmware buffer of TalonSRX.
	 * 
	 * @author Rohan Bapat
	 *
	 */
	class BufferProcessor implements Runnable {

		/**
		 * Makes call to move data
		 */
		public void run() {
			mElevatorTalon.processMotionProfileBuffer();
		}
	}

	/**
	 * Runnable used to take queued commands and convert them to trapezoidal motion
	 * profile trajectory points.
	 * 
	 * @author Rohan Bapat
	 * 
	 */
	class ProfileStreamer implements Runnable {

		/**
		 * default constructor
		 */
		private ElevatorPosition target;

		public ProfileStreamer() {
			target = targetLevel;
		}

		/**
		 * Thread-body that runs every 5 milliseconds; checks if it needs to generate
		 * new trajectory points to be streamed and then streams enough points such that
		 * it is 250ms ahead of the TalonSRX.
		 */
		public void run() {
			synchronized (this) {

				if (target != targetLevel || getDelta(elevatorLevel) > Constants.kElevatorThreshhold) {
					clearEverything();
					target = targetLevel;
					double[] jniTargets = ProfileGeneratorJNI.createNewProfile(Constants.kItp, Constants.kT1,
							Constants.kT2, Constants.kVprog, (encoderLevels[targetLevel.ordinal()] - currentCount)
									/ (Constants.kElevatorUnitsPerRotation * Constants.kElevatorRotationsPerInch));
					double[] targetVelocities = Arrays.copyOfRange(jniTargets, 1, jniTargets.length);
					prof.setGenerated(targetVelocities, mElevatorTalon.getSelectedSensorPosition(0));
					bufferProcessor.startPeriodic(0.005);
					LEDStrip.sendColor(LEDMode.eBlue);
				}
				int amount = Math.max(25 - (profileStatus.btmBufferCnt + profileStatus.topBufferCnt), 0);
				prof.pushBuffer(mElevatorTalon, amount);

			}
		}
	}

}
