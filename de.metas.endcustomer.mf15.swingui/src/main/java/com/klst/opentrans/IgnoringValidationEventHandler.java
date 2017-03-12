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

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;

import org.slf4j.Logger;

import de.metas.logging.LogManager;

/**
 *  Disabling schema validation while unmarshalling
 *  
 *  @see 2.3. Binding Framework in The Java™ Architecture for XML Binding (JAXB) 2.0
 */
/* 
 * SO-Dokumente sind nicht valide
 * 
 * @see http://stackoverflow.com/questions/4184597/disable-validation-for-jaxb-1-marshaller
 */
public class IgnoringValidationEventHandler implements ValidationEventHandler {

	protected final Logger log = LogManager.getLogger(getClass());
	private static final boolean CONTINUE_UNMARSHAL = true;

	@Override
	public boolean handleEvent(ValidationEvent event) {
//		event.getLocator().getNode();
		log.debug(event.getMessage());
		return CONTINUE_UNMARSHAL;
	}

}
