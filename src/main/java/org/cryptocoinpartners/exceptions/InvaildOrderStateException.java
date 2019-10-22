package org.cryptocoinpartners.exceptions;

/**
 * <p>
 * Exception to provide the following to:
 * </p>
 * <ul>
 * <li>Indication of generic Exchange exception</li>
 * </ul>
 */
public class InvaildOrderStateException extends RuntimeException {

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message.
   * 
   * @param message the detail message.
   */
  public InvaildOrderStateException(String message) {

    super(message);
  }

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message and cause.
   * 
   * @param message the detail message.
   * @param cause the underlying cause.
   */
  public InvaildOrderStateException(String message, Throwable cause) {

    super(message, cause);
  }
}
