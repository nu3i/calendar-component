/*
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.addon.calendar.handler;

import org.vaadin.addon.calendar.event.CalendarItem;
import org.vaadin.addon.calendar.event.EditableCalendarItem;
import org.vaadin.addon.calendar.ui.CalendarComponentEvents;

import java.util.Date;

/**
 * Implements basic functionality needed to enable event resizing.
 *
 * @since 7.1
 * @author Vaadin Ltd.
 */
@SuppressWarnings("serial")

public class BasicItemResizeHandler implements CalendarComponentEvents.EventResizeHandler {

    /*
     * (non-Javadoc)
     *
     * @see
     * org.vaadin.addon.calendar.ui.CalendarComponentEvents.EventResizeHandler
     * #itemResize
     * (org.vaadin.addon.calendar.ui.CalendarComponentEvents.ItemResizeEvent)
     */
    @Override
    public void itemResize(CalendarComponentEvents.ItemResizeEvent event) {
        CalendarItem calendarItem = event.getCalendarItem();

        if (calendarItem instanceof EditableCalendarItem) {
            Date newStartTime = event.getNewStart();
            Date newEndTime = event.getNewEnd();

            EditableCalendarItem editableItem = (EditableCalendarItem) calendarItem;

            setDates(editableItem, newStartTime, newEndTime);
        }
    }

    /**
     * Set the start and end dates for the event
     *
     * @param event
     *            The event that the start and end dates should be set
     * @param start
     *            The start date
     * @param end
     *            The end date
     */
    protected void setDates(EditableCalendarItem event, Date start, Date end) {
        event.setStart(start);
        event.setEnd(end);
    }
}
