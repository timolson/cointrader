package org.cryptocoinpartners.util;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

import org.cryptocoinpartners.schema.Market;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class PromptsUtil {

	public static ZonedDateTime getExpiryDate(Market market, ZonedDateTime date) {
		ZonedDateTime expiryDate = date;
		if (market.getListing().getPrompt() == null)
			return expiryDate;
		switch (market.getListing().getPrompt().getSymbol()) {
			case "THISWEEK":
				expiryDate = getNextFriday(date);
				break;
			case "NEXTWEEK":
				expiryDate = getFridayAfterNext(date);
				break;
			case "QUARTER":
				expiryDate = getLastFridayOfQuarter(date);
				break;
		}
		return expiryDate;
	}

	private static ZonedDateTime getLastFridayOfQuarter(ZonedDateTime date) {
		ZoneId localZone = date.getZone();
		ZoneId targetZone = ZoneId.of("Hongkong");
		ZonedDateTime dateTimeHK = date.withZoneSameInstant(targetZone);
		ZonedDateTime dateHK = date.withZoneSameInstant(targetZone).with(LocalTime.of(16, 0));
		// get last day in previous quarter
		long lastDayOfQuarter = IsoFields.DAY_OF_QUARTER.rangeRefinedBy(dateHK).getMaximum();

		ZonedDateTime quater = dateHK.with(IsoFields.DAY_OF_QUARTER, lastDayOfQuarter);

		// get the date corresponding to the last day of quarter
		ZonedDateTime lastFirdayOfQuarter = quater.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
		ZonedDateTime localTime = lastFirdayOfQuarter.withZoneSameInstant(localZone);
		long diff = ChronoUnit.DAYS.between(date, localTime);

		//Current quaterly becomes bi-weekly so we need next quartley
		//if today 

		if ((diff < 14)) {

			ZonedDateTime nextMonth = date.with(TemporalAdjusters.firstDayOfNextMonth());
			localTime = getLastFridayOfQuarter(nextMonth);
		}
		return localTime;

	}

	private static ZonedDateTime getNextFriday(ZonedDateTime date) {
		ZoneId localZone = date.getZone();

		ZoneId targetZone = ZoneId.of("Hongkong");
		ZonedDateTime dateHK = date.withZoneSameInstant(targetZone).with(LocalTime.of(16, 0));
		ZonedDateTime nextFriday;
		if (dateHK.getDayOfWeek().equals(DayOfWeek.FRIDAY) && date.withZoneSameInstant(targetZone).getHour() < 16)
			nextFriday = dateHK;
		else
			nextFriday = dateHK.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		ZonedDateTime localTime = nextFriday.withZoneSameInstant(localZone);

		return localTime;

	}

	private static ZonedDateTime getFridayAfterNext(ZonedDateTime date) {
		ZoneId localZone = date.getZone();
		ZoneId targetZone = ZoneId.of("Hongkong");
		ZonedDateTime dateHK = date.withZoneSameInstant(targetZone).with(LocalTime.of(16, 0));
		ZonedDateTime nextFriday;
		if (dateHK.getDayOfWeek().equals(DayOfWeek.FRIDAY) && date.withZoneSameInstant(targetZone).getHour() < 16)
			nextFriday = dateHK.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		else
			nextFriday = dateHK.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));

		ZonedDateTime localTime = nextFriday.withZoneSameInstant(localZone);

		return localTime;

	}

	public static Logger log = LoggerFactory.getLogger(PromptsUtil.class);

}
