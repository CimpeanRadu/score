/*******************************************************************************
* (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License v2.0 which accompany this distribution.
*
* The Apache License is available at
* http://www.apache.org/licenses/LICENSE-2.0
*
*******************************************************************************/

package io.cloudslang.engine.queue.repositories.callbacks;

import io.cloudslang.engine.partitions.services.PartitionCallback;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Date: 4/20/13
 *
 * @author
 */
abstract class AbstractCallback implements PartitionCallback {
	private final Logger logger = Logger.getLogger(getClass());

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Override
	public void doCallback(String previousTable, String activeTable) {
		if (logger.isDebugEnabled()) logger.debug(getClass().getSimpleName() + ": process from " + previousTable + " to " + activeTable);

		final String sql = getSql(previousTable, activeTable);
		if (logger.isDebugEnabled()) logger.debug(getClass().getSimpleName() + " Execute SQL: " + sql);
		try{
			long t = System.currentTimeMillis();
			int numOfRows = transactionTemplate.execute(new TransactionCallback<Integer>() {
				@Override
				public Integer doInTransaction(TransactionStatus status) {
					return jdbcTemplate.update(sql);
				}});
			t = System.currentTimeMillis()-t;
			if (logger.isDebugEnabled()) logger.debug(getClass().getSimpleName() + ": " + numOfRows  + " rows where processed in " + t + " ms");
            else if(t > TimeUnit.MINUTES.toMillis(1)) logger.warn("Rolling between table "+previousTable+" to table "+activeTable+", took :"+ t + " ms");
		} catch (DataAccessException ex){
			logger.error(getClass().getSimpleName() + " failed to execute: " + sql, ex);
		}
	}

	protected abstract String getSql(String previousTable, String activeTable);
}
