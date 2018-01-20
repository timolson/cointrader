package org.cryptocoinpartners.report;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang.ArrayUtils;
import org.cryptocoinpartners.schema.dao.ReportDao;
import org.cryptocoinpartners.util.Visitor;
import org.slf4j.Logger;

/**
 * @author Tim Olson
 */
public abstract class JpaReport implements Report {
	@Inject
	protected ReportDao reportDao;

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	@Override
	public TableOutput runReport() {
		final Query query = getQuery();
		final List<String[]> rowStrings = new ArrayList<>();

		if (log.isTraceEnabled())
			log.trace("Querying: " + query.queryStr + " / " + ArrayUtils.toString(query.params));
		if (limit != 0) {
			Visitor<Object[]> visitor = new Visitor<Object[]>() {
				private int count = 0;

				@Override
				public boolean handleItem(Object[] row) {
					handleResult(row, rowStrings);
					return ++count < limit;
				}
			};
			reportDao.queryEach(visitor, limit, query.queryStr, query.params);
		} else {
			Visitor<Object[]> visitor = new Visitor<Object[]>() {
				@Override
				public boolean handleItem(Object[] row) {
					handleResult(row, rowStrings);
					return true;
				}
			};
			reportDao.queryEach(visitor, 1, query.queryStr, query.params);
		}
		String[][] rowStringTable = new String[rowStrings.size()][];
		rowStrings.toArray(rowStringTable);
		final String[] headers = query.headers;
		return new TableOutput(headers, rowStringTable);
	}

	protected void handleResult(Object[] row, List<String[]> rowStrings) {
		final String[] rowFormat = formatRow(row);
		rowStrings.add(rowFormat);
	}

	@SuppressWarnings("UnusedDeclaration")
	public static class Query {
		public Query(String[] headers, String queryStr, Object[] params) {
			this.headers = headers;
			this.queryStr = queryStr;
			this.params = params;
		}

		public Query(String[] headers, String queryStr) {
			this.headers = headers;
			this.queryStr = queryStr;
			this.params = new Object[] {};
		}

		String[] headers;
		String queryStr;
		Object[] params;
	}

	protected abstract Query getQuery();

	protected String[] formatRow(Object[] row) {
		final String[] result = new String[row.length];
		for (int i = 0; i < row.length; i++) {
			Object item = row[i];
			result[i] = formatColumn(i, item);
		}
		return result;
	}

	protected String formatColumn(@SuppressWarnings("UnusedParameters") int columnIndex, Object item) {
		return String.valueOf(item);
	}

	@Inject
	private Logger log;

	private int limit;
}
