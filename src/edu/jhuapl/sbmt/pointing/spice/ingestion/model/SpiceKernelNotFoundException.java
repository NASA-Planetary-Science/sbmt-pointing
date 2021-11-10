package edu.jhuapl.sbmt.pointing.spice.ingestion.model;

/**
 * Exception thrown when performing I/O operations on
 * a State History segment
 * @author steelrj1
 *
 */
public class SpiceKernelNotFoundException extends Exception
{

	/**
	 *
	 */
	public SpiceKernelNotFoundException()
	{
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 */
	public SpiceKernelNotFoundException(String message)
	{
		super(message);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param cause
	 */
	public SpiceKernelNotFoundException(Throwable cause)
	{
		super(cause);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 * @param cause
	 */
	public SpiceKernelNotFoundException(String message, Throwable cause)
	{
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 * @param cause
	 * @param enableSuppression
	 * @param writableStackTrace
	 */
	public SpiceKernelNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

}
