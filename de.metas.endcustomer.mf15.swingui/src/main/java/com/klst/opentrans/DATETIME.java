/*
 * #%L
 * de.metas.endcustomer.mf15.swingui
 * %%
 * Copyright (C) 2016 klst GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package com.klst.opentrans;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.bmecat.bmecat._2005.TypeDATETIME;
import org.slf4j.Logger;

import de.metas.logging.LogManager;

public class DATETIME extends TypeDATETIME
{
	private static final Logger log = LogManager.getLogger(DATETIME.class);
	
	private static DatatypeFactory datatypeFactory = null;
	
	public DATETIME(String value) {
		super();
		this.setDATE(value);
	}
	
	public void setDATE(String value) {
		if(datatypeFactory==null) {
			try {
				datatypeFactory = DatatypeFactory.newInstance();
			} catch (DatatypeConfigurationException e) {
				e.printStackTrace();
			}
		}
		try {
			XMLGregorianCalendar xmlGregCal = datatypeFactory.newXMLGregorianCalendar(value);
			super.setDATE(xmlGregCal);
			return;
		} catch (IllegalArgumentException e) {
			log.warn("setDATE: {}, will try dmy-formats.",e.toString());
		}
		Date date = setDATE(value, "dd.MM.yyyy");
		if(date==null) setDATE(value, "dd.MMM.yyyy");
	}

	private Date setDATE(String value, String format) {
		SimpleDateFormat dmy = new SimpleDateFormat( format );
		Date date = null;
		try {
			date = dmy.parse(value);
			log.debug("setDATE value={} format={} date={}", value, format, date);
			GregorianCalendar gregCal = new GregorianCalendar();
			gregCal.setTime(date);
			XMLGregorianCalendar xmlGregCal = datatypeFactory.newXMLGregorianCalendar(gregCal);
			super.setDATE(xmlGregCal);
		} catch (ParseException e) {
			log.warn("setDATE ParseException {}, assumed format={}", e.toString(), format);
		}
		return date;
	}
	
	public Timestamp getTimestamp() {
		GregorianCalendar gregCal = this.getDATE().toGregorianCalendar();
		Date dateObj = gregCal.getTime();
		return new Timestamp(dateObj.getTime());
	}
	
	// -------------------- TODO test auslagern
	public static void main(String[] args) {
		DATETIME dt = new DATETIME("2009-05-13T06:20:00+01:00");
		log.info(" DATE={} Timestamp={}", dt.getDATE(), dt.getTimestamp());
		assert(dt.getTIME()==null);
		
		dt.setDATE("09.09.2014");
		log.info(" DATE={} Timestamp={}", dt.getDATE(), dt.getTimestamp());
		assert(dt.getTimestamp().toString().equals("2014-09-09 00:00:00.0"));
	}

}
