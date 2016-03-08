package org.cryptocoinpartners.exceptions;

/**
 * <p>
 * Exception to provide the following to:
 * </p>
 * <ul>
 * <li>Indication of generic Exchange exception</li>
 * </ul>
 */
public class TradingDisabledException extends RuntimeException {

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message.
   * 
   * @param message the detail message.
   */
  public TradingDisabledException(String message) {

    super(message);
  }

  /**
   * Constructs an <code>ExchangeException</code> with the specified detail message and cause.
   * 
   * @param message the detail message.
   * @param cause the underlying cause.
   */
  public TradingDisabledException(String message, Throwable cause) {

    super(message, cause);
  }
}
