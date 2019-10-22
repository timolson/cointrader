package org.cryptocoinpartners.exceptions;

/**
 * <p>
 * Exception to provide the following to:
 * </p>
 * <ul>
 * <li>Indication of generic Exchange exception</li>
 * </ul>
 */
public class ConcurrentWorkingOrdersException extends RuntimeException {

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message.
   * 
   * @param message the detail message.
   */
  public ConcurrentWorkingOrdersException(String message) {

    super(message);
  }

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message and cause.
   * 
   * @param message the detail message.
   * @param cause the underlying cause.
   */
  public ConcurrentWorkingOrdersException(String message, Throwable cause) {

    super(message, cause);
  }
}
