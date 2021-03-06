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

import org.vaadin.addon.calendar.ui.CalendarComponentEvents;

import java.util.Calendar;
import java.util.Date;

/**
 * Implements basic functionality needed to enable forward navigation.
 *
 * @since 7.1
 * @author Vaadin Ltd.
 */

@SuppressWarnings("serial")
public class BasicForwardHandler implements CalendarComponentEvents.ForwardHandler {

    /*
     * (non-Javadoc)
     *
     * @see org.vaadin.addon.calendar.ui.CalendarComponentEvents.ForwardHandler#
     * forward
     * (org.vaadin.addon.calendar.ui.CalendarComponentEvents.ForwardEvent)
     */
    @Override
    public void forward(CalendarComponentEvents.ForwardEvent event) {

        int firstDay = event.getComponent().getFirstVisibleDayOfWeek();
        int lastDay = event.getComponent().getLastVisibleDayOfWeek();
        Date start = event.getComponent().getStartDate();
        Date end = event.getComponent().getEndDate();

        int durationInDays = 0;

        // for week view durationInDays = 7, for day view durationInDays = 1
        if (event.getComponent().isDayMode()) { // day view
            durationInDays = 1;
        } else if (event.getComponent().isWeeklyMode()) {
            durationInDays = 7;
        }

        // set new start and end times
        Calendar javaCalendar = Calendar.getInstance(event.getComponent().getInternalCalendar().getTimeZone());
        javaCalendar.setTime(start);
        javaCalendar.add(Calendar.DAY_OF_MONTH, durationInDays);
        Date newStart = javaCalendar.getTime();

        javaCalendar.setTime(end);
        javaCalendar.add(Calendar.DAY_OF_MONTH, durationInDays);
        Date newEnd = javaCalendar.getTime();

        if (event.getComponent().isDayMode()) { // day view

            int dayOfWeek = javaCalendar.get(Calendar.DAY_OF_WEEK);

            while (!(firstDay <= dayOfWeek && dayOfWeek <= lastDay)) {
                javaCalendar.add(Calendar.DAY_OF_MONTH, 1);
                dayOfWeek = javaCalendar.get(Calendar.DAY_OF_WEEK);
            }

            newStart = newEnd = javaCalendar.getTime();
        }

        setDates(event, newStart, newEnd);

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
    protected void setDates(CalendarComponentEvents.ForwardEvent event, Date start, Date end) {
        event.getComponent().setStartDate(start);
        event.getComponent().setEndDate(end);
    }
}
