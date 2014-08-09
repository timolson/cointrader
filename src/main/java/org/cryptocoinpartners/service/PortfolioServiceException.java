package org.cryptocoinpartners.service;

import org.apache.commons.beanutils.PropertyUtils;

public class PortfolioServiceException extends RuntimeException {

	public PortfolioServiceException() {
		// Documented empty block
	}

	public PortfolioServiceException(Throwable throwable) {
		super(findRootCause(throwable));
	}

	public PortfolioServiceException(String message) {
		super(message);
	}

	public PortfolioServiceException(String message, Throwable throwable) {
		super(message, findRootCause(throwable));
	}

	private static Throwable findRootCause(Throwable th) {
		if (th != null) {
			// Reflectively get any exception causes.
			try {
				Throwable targetException = null;

				// java.lang.reflect.InvocationTargetException
				String exceptionProperty = "targetException";
				if (PropertyUtils.isReadable(th, exceptionProperty)) {
					targetException = (Throwable) PropertyUtils.getProperty(th, exceptionProperty);
				} else {
					exceptionProperty = "causedByException";
					//javax.ejb.EJBException
					if (PropertyUtils.isReadable(th, exceptionProperty)) {
						targetException = (Throwable) PropertyUtils.getProperty(th, exceptionProperty);
					}
				}
				if (targetException != null) {
					th = targetException;
				}
			} catch (Exception ex) {
				// just print the exception and continue
				ex.printStackTrace();
			}

			if (th.getCause() != null) {
				th = th.getCause();
				th = findRootCause(th);
			}
		}
		return th;
	}

	private Object[] messageArguments;

	public Object[] getMessageArguments() {
		return this.messageArguments;
	}

	public void setMessageArguments(Object[] messageArgumentsIn) {
		this.messageArguments = messageArgumentsIn;
	}
}
