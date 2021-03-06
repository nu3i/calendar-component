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
package org.vaadin.addon.calendar;

import com.vaadin.event.Action;
import com.vaadin.event.Action.Handler;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.DropTarget;
import com.vaadin.event.dd.TargetDetails;
import com.vaadin.server.KeyMapper;
import com.vaadin.server.PaintException;
import com.vaadin.server.PaintTarget;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.LegacyComponent;
import com.vaadin.ui.declarative.DesignAttributeHandler;
import com.vaadin.ui.declarative.DesignContext;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Element;
import org.vaadin.addon.calendar.client.CalendarEventId;
import org.vaadin.addon.calendar.client.CalendarServerRpc;
import org.vaadin.addon.calendar.client.CalendarState;
import org.vaadin.addon.calendar.client.DateConstants;
import org.vaadin.addon.calendar.event.*;
import org.vaadin.addon.calendar.handler.*;
import org.vaadin.addon.calendar.ui.CalendarComponentEvent;
import org.vaadin.addon.calendar.ui.CalendarComponentEvents;
import org.vaadin.addon.calendar.ui.CalendarDateRange;
import org.vaadin.addon.calendar.ui.CalendarTargetDetails;

import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>
 * Vaadin Calendar is for visualizing items in a calendar. Calendar items can
 * be visualized in the variable length view depending on the start and end
 * dates.
 * </p>
 *
 * <li>You can set the viewable date range with the {@link #setStartDate(Date)}
 * and {@link #setEndDate(Date)} methods. Calendar has a default date range of
 * one week</li>
 *
 * <li>Calendar has two kind of views: monthly and weekly view</li>
 *
 * <li>If date range is seven days or shorter, the weekly view is used.</li>
 *
 * <li>Calendar queries its items by using a {@link CalendarItemProvider}. By
 * default, a {@link BasicItemProvider} is used.</li>
 *
 * @since 7.1
 * @author Vaadin Ltd.
 *
 */
@SuppressWarnings("serial")

public class Calendar<ITEM extends EditableCalendarItem> extends AbstractComponent implements
        CalendarComponentEvents.NavigationNotifier,
        CalendarComponentEvents.ItemMoveNotifier,
        CalendarComponentEvents.RangeSelectNotifier,
        CalendarComponentEvents.ItemResizeNotifier,
        CalendarItemProvider.ItemSetChangedListener,
        DropTarget,
        Action.Container,
        LegacyComponent,
        CalendarItemProvider<ITEM> {

    /**
     * Calendar can use either 12 hours clock or 24 hours clock.
     */
    @Deprecated
    public enum TimeFormat {
        Format12H(), Format24H()
    }

    /** Defines currently active format for time. 12H/24H. */
    protected TimeFormat currentTimeFormat;

    /** Internal calendar data source. */
    protected java.util.Calendar currentCalendar = java.util.Calendar.getInstance();

    /** Defines the component's active time zone. */
    protected TimeZone timezone;

    /** Defines the calendar's date range starting point. */
    protected Date startDate = null;

    /** Defines the calendar's date range ending point. */
    protected Date endDate = null;

    /** Item provider. */
    private CalendarItemProvider<ITEM> calendarItemProvider;

    /**
     * Internal buffer for the items that are retrieved from the item provider.
     */
    protected List<? extends CalendarItem> items;

    /** Date format that will be used in the UIDL for dates. */
    protected DateFormat df_date = new SimpleDateFormat("yyyy-MM-dd");

    /** Time format that will be used in the UIDL for time. */
    protected DateFormat df_time = new SimpleDateFormat("HH:mm:ss");

    /** Date format that will be used in the UIDL for both date and time. */
    protected DateFormat df_date_time = new SimpleDateFormat(
            DateConstants.CLIENT_DATE_FORMAT + "-"
                    + DateConstants.CLIENT_TIME_FORMAT);

    /**
     * Week view's scroll position. Client sends updates to this value so that
     * scroll position wont reset all the time.
     */
    private int scrollTop = 0;

    /** Caption format for the weekly view */
    private String weeklyCaptionFormat = null;

    /** Map from event ids to event handlers */
    private final Map<String, EventListener> handlers;

    /**
     * Drop Handler for Vaadin DD. By default null.
     */
    private DropHandler dropHandler;

    /**
     * First day to show for a week
     */
    private int firstDay = 1;

    /**
     * Last day to show for a week
     */
    private int lastDay = 7;

    /**
     * First hour to show for a day
     */
    private int firstHour = 0;

    /**
     * Last hour to show for a day
     */
    private int lastHour = 23;

    /**
     * List of action handlers.
     */
    private LinkedList<Handler> actionHandlers = null;

    /**
     * Action mapper.
     */
    private KeyMapper<Action> actionMapper = null;

    /**
     *
     */
    private CalendarServerRpcImpl rpc = new CalendarServerRpcImpl();

    /**
     * The cached minimum minute shown when using
     * {@link #autoScaleVisibleHoursOfDay()}.
     */
    private Integer minTimeInMinutes;

    /**
     * The cached maximum minute shown when using
     * {@link #autoScaleVisibleHoursOfDay()}.
     */
    private Integer maxTimeInMinutes;

    private Integer customFirstDayOfWeek;

    /**
     * A map with blocked timeslots.<br>
     *     Contains a set with timestamp of starttimes.
     */
    private final Map<Date, Set<Long>> blockedTimes = new HashMap<>();

    /**
     * Initial date for all blocked times
     */
    private final Date allOverDate = new Date(0);

    /**
     * Returns the logger for the calendar
     */
    protected Logger getLogger() {
        return Logger.getLogger(Calendar.class.getName());
    }

    /**
     * Construct a Vaadin Calendar with a BasicItemProvider and no caption.
     * Default date range is one week.
     */
    public Calendar() {
        this(null, new BasicItemProvider());
    }

    /**
     * Construct a Vaadin Calendar with a BasicItemProvider and the provided
     * caption. Default date range is one week.
     *
     * @param caption
     */
    public Calendar(String caption) {
        this(caption, new BasicItemProvider());
    }

    /**
     * <p>
     * Construct a Vaadin Calendar with event provider. Item provider is
     * obligatory, because calendar component will query active items through
     * it.
     * </p>
     *
     * <p>
     * By default, Vaadin Calendar will show dates from the start of the current
     * week to the end of the current week. Use {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)} to change this.
     * </p>
     *
     * @param dataProvider
     *            Item provider, cannot be null.
     */
    public Calendar(CalendarItemProvider<ITEM> dataProvider) {
        this(null, dataProvider);
    }

    /**
     * <p>
     * Construct a Vaadin Calendar with item provider and a caption. Item
     * provider is obligatory, because calendar component will query active
     * items through it.
     * </p>
     *
     * <p>
     * By default, Vaadin Calendar will show dates from the start of the current
     * week to the end of the current week. Use {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)} to change this.
     * </p>
     *
     * @param dataProvider
     *            Item provider, cannot be null.
     */
    // this is the constructor every other constructor calls
    public Calendar(String caption, CalendarItemProvider<ITEM> dataProvider) {
        registerRpc(rpc);
        setCaption(caption);
        handlers = new HashMap<>();
        setDefaultHandlers();
        currentCalendar.setTime(new Date());
        setDataProvider(dataProvider);
        getState().firstDayOfWeek = firstDay;
        getState().lastVisibleDayOfWeek = lastDay;
        getState().firstHourOfDay = firstHour;
        getState().lastHourOfDay = lastHour;
        setTimeFormat(null);
    }

    @Override
    public CalendarState getState() {
        return (CalendarState) super.getState();
    }

    @Override
    protected CalendarState getState(boolean markAsDirty) {
        return (CalendarState) super.getState(markAsDirty);
    }

    @Override
    public void beforeClientResponse(boolean initial) {
        super.beforeClientResponse(initial);

        initCalendarWithLocale();

        getState().format24H = TimeFormat.Format24H == getTimeFormat();
        setupDaysAndActions();
        setupCalendarItems();
        rpc.scroll(scrollTop);
    }

    /**
     * Set the ContentMode
     *
     * @param contentMode The new content mode
     */
    public void setContentMode(ContentMode contentMode) {
        getState().descriptionContentMode = Objects.isNull(contentMode) ? ContentMode.PREFORMATTED : contentMode;
    }

    /**
     * @return The content mode
     */
    public ContentMode getContentMode() {
        return getState(false).descriptionContentMode;
    }

    /**
     * Set all the wanted default handlers here. This is always called after
     * constructing this object. All other items have default handlers except
     * range and event click.
     */
    protected void setDefaultHandlers() {
        setHandler(new BasicBackwardHandler());
        setHandler(new BasicForwardHandler());
        setHandler(new BasicWeekClickHandler());
        setHandler(new BasicDateClickHandler());
        setHandler(new BasicItemMoveHandler());
        setHandler(new BasicItemResizeHandler());
    }

    /**
     * Gets the calendar's start date.
     *
     * @return First visible date.
     */
    public Date getStartDate() {
        if (startDate == null) {
            currentCalendar.set(java.util.Calendar.MILLISECOND, 0);
            currentCalendar.set(java.util.Calendar.SECOND, 0);
            currentCalendar.set(java.util.Calendar.MINUTE, 0);
            currentCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
            currentCalendar.set(java.util.Calendar.DAY_OF_WEEK, currentCalendar.getFirstDayOfWeek());
            return currentCalendar.getTime();
        }
        return startDate;
    }

    /**
     * Sets start date for the calendar. This and {@link #setEndDate(Date)}
     * control the range of dates visible on the component. The default range is
     * one week.
     *
     * @param date
     *            First visible date to show.
     */
    public void setStartDate(Date date) {
        if (!date.equals(startDate)) {
            startDate = date;
            markAsDirty();
        }
    }

    /**
     * Gets the calendar's end date.
     *
     * @return Last visible date.
     */
    public Date getEndDate() {
        if (endDate == null) {
            currentCalendar.set(java.util.Calendar.MILLISECOND, 0);
            currentCalendar.set(java.util.Calendar.SECOND, 59);
            currentCalendar.set(java.util.Calendar.MINUTE, 59);
            currentCalendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
            currentCalendar.set(java.util.Calendar.DAY_OF_WEEK, currentCalendar.getFirstDayOfWeek() + 6);
            return currentCalendar.getTime();
        }
        return endDate;
    }

    /**
     * Sets end date for the calendar. Starting from startDate, only six weeks
     * will be shown if duration to endDate is longer than six weeks.
     *
     * This and {@link #setStartDate(Date)} control the range of dates visible
     * on the component. The default range is one week.
     *
     * @param date
     *            Last visible date to show.
     */
    public void setEndDate(Date date) {
        if (startDate != null && startDate.after(date)) {
            startDate = (Date) date.clone();
            markAsDirty();
        } else if (!date.equals(endDate)) {
            endDate = date;
            markAsDirty();
        }
    }

    /**
     * Sets the locale to be used in the Calendar component.
     *
     * @see AbstractComponent#setLocale(Locale)
     */
    @Override
    public void setLocale(Locale newLocale) {
        super.setLocale(newLocale);
        initCalendarWithLocale();
    }

    /**
     * Initialize the java calendar instance with the current locale and
     * timezone.
     */
    private void initCalendarWithLocale() {
        if (timezone != null) {
            currentCalendar = java.util.Calendar.getInstance(timezone, getLocale());

        } else {
            currentCalendar = java.util.Calendar.getInstance(getLocale());
        }

        if (customFirstDayOfWeek != null) {
            currentCalendar.setFirstDayOfWeek(customFirstDayOfWeek);
        }
    }

    private void setupCalendarItems() {

        int durationInDays = (int) ((endDate.getTime() - startDate.getTime()) / DateConstants.DAYINMILLIS);
        durationInDays++;

        if (durationInDays > 60) {
            throw new RuntimeException(
                    "Daterange is too big (max 60) = " + durationInDays);
        }

        Date firstDateToShow = expandStartDate(startDate, durationInDays > 7);
        Date lastDateToShow = expandEndDate(endDate, durationInDays > 7);

        currentCalendar.setTime(firstDateToShow);
        items = getDataProvider().getItems(firstDateToShow, lastDateToShow);
        cacheMinMaxTimeOfDay(items);

        List<CalendarState.Item> calendarStateItems = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                CalendarItem calItem = items.get(i);
                CalendarState.Item item = new CalendarState.Item();
                item.index = i;
                item.caption = calItem.getCaption() == null ? "" : calItem.getCaption();
                item.dateFrom = df_date.format(calItem.getStart());
                item.dateTo = df_date.format(calItem.getEnd());
                item.timeFrom = df_time.format(calItem.getStart());
                item.timeTo = df_time.format(calItem.getEnd());
                item.description = calItem.getDescription() == null ? "" : calItem.getDescription();
                item.styleName = calItem.getStyleName() == null ? "" : calItem.getStyleName();
                item.allDay = calItem.isAllDay();
                item.moveable = calItem.isMoveable();
                item.resizeable = calItem.isResizeable();
                item.clickable = calItem.isClickable();
                calendarStateItems.add(item);
            }
        }
        getState().items = calendarStateItems;
    }

    /**
     * Stores the minimum and maximum time-of-day in minutes for the items.
     *
     * @param items
     *            A list of calendar items. Can be <code>null</code>.
     */
    private void cacheMinMaxTimeOfDay(List<? extends CalendarItem> items) {
        minTimeInMinutes = null;
        maxTimeInMinutes = null;
        if (items != null) {
            for (CalendarItem item : items) {
                int minuteOfDayStart = getMinuteOfDay(item.getStart());
                int minuteOfDayEnd = getMinuteOfDay(item.getEnd());
                if (minTimeInMinutes == null) {
                    minTimeInMinutes = minuteOfDayStart;
                    maxTimeInMinutes = minuteOfDayEnd;
                } else {
                    if (minuteOfDayStart < minTimeInMinutes) {
                        minTimeInMinutes = minuteOfDayStart;
                    }
                    if (minuteOfDayEnd > maxTimeInMinutes) {
                        maxTimeInMinutes = minuteOfDayEnd;
                    }
                }
            }
        }
    }

    private static int getMinuteOfDay(Date date) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + calendar.get(java.util.Calendar.MINUTE);
    }

    /**
     * Sets the displayed start and end time to fit all current items that were
     * retrieved from the last call to getItems().
     * <p>
     * If no items exist, nothing happens.
     * <p>
     * <b>NOTE: triggering this method only does this once for the current
     * items - items that are not in the current visible range, are
     * ignored!</b>
     *
     * @see #setFirstVisibleHourOfDay(int)
     * @see #setLastVisibleHourOfDay(int)
     */
    public void autoScaleVisibleHoursOfDay() {
        if (minTimeInMinutes != null) {
            setFirstVisibleHourOfDay(minTimeInMinutes / 60);
            // Do not show the final hour if last minute ends on it
            setLastVisibleHourOfDay((maxTimeInMinutes - 1) / 60);
        }
    }

    /**
     * Resets the {@link #setFirstVisibleHourOfDay(int)} and
     * {@link #setLastVisibleHourOfDay(int)} to the default values, 0 and 23
     * respectively.
     *
     * @see #autoScaleVisibleHoursOfDay()
     * @see #setFirstVisibleHourOfDay(int)
     * @see #setLastVisibleHourOfDay(int)
     */
    public void resetVisibleHoursOfDay() {
        setFirstVisibleHourOfDay(0);
        setLastVisibleHourOfDay(23);
    }

    private void setupDaysAndActions() {
        // Make sure we have a up-to-date locale
        initCalendarWithLocale();

        CalendarState state = getState();

        state.firstDayOfWeek = currentCalendar.getFirstDayOfWeek();

        // If only one is null, throw exception
        // If both are null, set defaults
        if (startDate == null ^ endDate == null) {
            String message = "Schedule cannot be painted without a proper date range.\n";
            if (startDate == null) {
                throw new IllegalStateException(message
                        + "You must set a start date using setStartDate(Date).");

            } else {
                throw new IllegalStateException(message
                        + "You must set an end date using setEndDate(Date).");
            }

        } else if (startDate == null) {
            // set defaults
            startDate = getStartDate();
            endDate = getEndDate();
        }

        int durationInDays = (int) ((endDate.getTime() - startDate.getTime())/ DateConstants.DAYINMILLIS);
        durationInDays++;
        if (durationInDays > 60) {
            throw new RuntimeException( "Daterange is too big (max 60) = " + durationInDays);
        }

        state.dayNames = getDayNamesShort();
        state.monthNames = getMonthNamesShort();

        // Use same timezone in all dates this component handles.
        // Show "now"-marker in browser within given timezone.
        Date now = new Date();
        currentCalendar.setTime(now);
        now = currentCalendar.getTime();

        // Reset time zones for custom date formats
        df_date.setTimeZone(currentCalendar.getTimeZone());
        df_time.setTimeZone(currentCalendar.getTimeZone());

        state.now = df_date.format(now) + " " + df_time.format(now);

        Date firstDateToShow = expandStartDate(startDate, durationInDays > 7);
        Date lastDateToShow = expandEndDate(endDate, durationInDays > 7);

        currentCalendar.setTime(firstDateToShow);

        DateFormat weeklyCaptionFormatter = getWeeklyCaptionFormatter();
        weeklyCaptionFormatter.setTimeZone(currentCalendar.getTimeZone());

        Map<CalendarDateRange, Set<Action>> actionMap = new HashMap<>();

        List<CalendarState.Day> days = new ArrayList<>();

        // Send all dates to client from server. This
        // approach was taken because gwt doesn't
        // support date localization properly.
        while (currentCalendar.getTime().compareTo(lastDateToShow) < 1) {

            final Date date = currentCalendar.getTime();

            final CalendarState.Day day = new CalendarState.Day();

            day.date = df_date.format(date);
            day.localizedDateFormat = weeklyCaptionFormatter.format(date);
            day.dayOfWeek = getDowByLocale(currentCalendar);
            day.week = currentCalendar.get(java.util.Calendar.WEEK_OF_YEAR);
            day.yearOfWeek = currentCalendar.getWeekYear();

            // XXX block time slots
            day.blockedSlots = new HashSet<>();

            if (blockedTimes.containsKey(allOverDate)) {
                day.blockedSlots.addAll(blockedTimes.get(allOverDate));
            }

            if (blockedTimes.containsKey(date)) {
                day.blockedSlots.addAll(blockedTimes.get(date));
            }

            days.add(day);

            // Get actions for a specific date
            if (actionHandlers != null) {

                for (Action.Handler actionHandler : actionHandlers) {

                    // Create calendar which omits time
                    GregorianCalendar cal = new GregorianCalendar(getTimeZone(), getLocale());
                    cal.clear();
                    cal.set(currentCalendar.get(java.util.Calendar.YEAR),
                            currentCalendar.get(java.util.Calendar.MONTH),
                            currentCalendar.get(java.util.Calendar.DATE));

                    // Get day start and end times
                    Date start = cal.getTime();
                    cal.add(java.util.Calendar.DATE, 1);
                    cal.add(java.util.Calendar.SECOND, -1);
                    Date end = cal.getTime();

                    boolean monthView = durationInDays > 7;

                    /*
                     * If in day or week view add actions for each half-an-hour.
                     * If in month view add actions for each day
                     */

                    if (monthView) {
                        setActionsForDay(actionMap, start, end, actionHandler);
                    } else {
                        setActionsForEachHalfHour(actionMap, start, end, actionHandler);
                    }

                }
            }

            currentCalendar.add(java.util.Calendar.DATE, 1);
        }

        state.days = days;
        state.actions = createActionsList(actionMap);
    }

    private void setActionsForEachHalfHour(Map<CalendarDateRange, Set<Action>> actionMap,
                                           Date start, Date end, Action.Handler actionHandler) {

        GregorianCalendar cal = new GregorianCalendar(getTimeZone(),  getLocale());
        cal.setTime(start);

        while (cal.getTime().before(end)) {

            Date s = cal.getTime();
            cal.add(java.util.Calendar.MINUTE, 30);

            Date e = cal.getTime();
            CalendarDateRange range = new CalendarDateRange(s, e, getTimeZone());

            Action[] actions = actionHandler.getActions(range, this);
            if (actions != null) {
                Set<Action> actionSet = new LinkedHashSet<>(Arrays.asList(actions));
                actionMap.put(range, actionSet);
            }
        }
    }

    private void setActionsForDay(Map<CalendarDateRange, Set<Action>> actionMap,
                                  Date start, Date end, Action.Handler actionHandler) {

        CalendarDateRange range = new CalendarDateRange(start, end, getTimeZone());
        Action[] actions = actionHandler.getActions(range, this);
        if (actions != null) {
            Set<Action> actionSet = new LinkedHashSet<>(Arrays.asList(actions));
            actionMap.put(range, actionSet);
        }
    }

    private List<CalendarState.Action> createActionsList(
            Map<CalendarDateRange, Set<Action>> actionMap) {
        if (actionMap.isEmpty()) {
            return null;
        }

        List<CalendarState.Action> calendarActions = new ArrayList<>();

        SimpleDateFormat formatter = new SimpleDateFormat(
                DateConstants.ACTION_DATE_FORMAT_PATTERN);
        formatter.setTimeZone(getTimeZone());

        for (Entry<CalendarDateRange, Set<Action>> entry : actionMap
                .entrySet()) {
            CalendarDateRange range = entry.getKey();
            Set<Action> actions = entry.getValue();
            for (Action action : actions) {
                String key = actionMapper.key(action);
                CalendarState.Action calendarAction = new CalendarState.Action();
                calendarAction.actionKey = key;
                calendarAction.caption = action.getCaption();
                setResource(key, action.getIcon());
                calendarAction.iconKey = key;
                calendarAction.startDate = formatter.format(range.getStart());
                calendarAction.endDate = formatter.format(range.getEnd());
                calendarActions.add(calendarAction);
            }
        }

        return calendarActions;
    }

    /**
     * Gets currently active time format. Value is either TimeFormat.Format12H
     * or TimeFormat.Format24H.
     *
     * @return TimeFormat Format for the time.
     */
    public TimeFormat getTimeFormat() {
        if (currentTimeFormat == null) {
            SimpleDateFormat f;
            if (getLocale() == null) {
                f = (SimpleDateFormat) SimpleDateFormat
                        .getTimeInstance(SimpleDateFormat.SHORT);
            } else {
                f = (SimpleDateFormat) SimpleDateFormat
                        .getTimeInstance(SimpleDateFormat.SHORT, getLocale());
            }
            String p = f.toPattern();
            if (p.contains("H")) {
                return TimeFormat.Format24H;
            }
            return TimeFormat.Format12H;
        }
        return currentTimeFormat;
    }

    /**
     * Example: <code>setTimeFormat(TimeFormat.Format12H);</code></br>
     * Set to null, if you want the format being defined by the locale.
     *
     * @param format
     *            Set 12h or 24h format. Default is defined by the locale.
     */
    public void setTimeFormat(TimeFormat format) {
        currentTimeFormat = format;
        markAsDirty();
    }

    /**
     * Returns a time zone that is currently used by this component.
     *
     * @return Component's Time zone
     */
    public TimeZone getTimeZone() {
        if (timezone == null) {
            return currentCalendar.getTimeZone();
        }
        return timezone;
    }

    /**
     * Set time zone that this component will use. Null value sets the default
     * time zone.
     *
     * @param zone
     *            Time zone to use
     */
    public void setTimeZone(TimeZone zone) {
        timezone = zone;
        if (!currentCalendar.getTimeZone().equals(zone)) {
            if (zone == null) {
                zone = TimeZone.getDefault();
            }
            currentCalendar.setTimeZone(zone);
            df_date_time.setTimeZone(zone);
            markAsDirty();
        }
    }

    /**
     * Get the internally used Calendar instance. This is the currently used
     * instance of {@link java.util.Calendar} but is bound to change during the
     * lifetime of the component.
     *
     * @return the currently used java calendar
     */
    public java.util.Calendar getInternalCalendar() {
        return currentCalendar;
    }

    /**
     * <p>
     * This method restricts the weekdays that are shown. This affects both the
     * monthly and the weekly view. The general contract is that <b>firstDay <
     * lastDay</b>.
     * </p>
     *
     * <p>
     * Note that this only affects the rendering process. Items are still
     * requested by the dates set by {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)}.
     * </p>
     *
     * @param firstDay
     *            the first day of the week to show, between 1 and 7
     */
    public void setFirstVisibleDayOfWeek(int firstDay) {
        if (this.firstDay != firstDay && firstDay >= 1 && firstDay <= 7 && getLastVisibleDayOfWeek() >= firstDay) {
            this.firstDay = firstDay;
            getState().firstVisibleDayOfWeek = firstDay;
        }
    }

    /**
     * Get the first visible day of the week. Returns the weekdays as integers
     * represented by {@link java.util.Calendar#DAY_OF_WEEK}
     *
     * @return An integer representing the week day according to
     *         {@link java.util.Calendar#DAY_OF_WEEK}
     */
    public int getFirstVisibleDayOfWeek() {
        return firstDay;
    }

    /**
     * <p>
     * This method restricts the weekdays that are shown. This affects both the
     * monthly and the weekly view. The general contract is that <b>firstDay <
     * lastDay</b>.
     * </p>
     *
     * <p>
     * Note that this only affects the rendering process. Items are still
     * requested by the dates set by {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)}.
     * </p>
     *
     * @param lastDay
     *            the first day of the week to show, between 1 and 7
     */
    public void setLastVisibleDayOfWeek(int lastDay) {
        if (this.lastDay != lastDay && lastDay >= 1 && lastDay <= 7 && getFirstVisibleDayOfWeek() <= lastDay) {
            this.lastDay = lastDay;
            getState().lastVisibleDayOfWeek = lastDay;
        }
    }

    /**
     * Get the last visible day of the week. Returns the weekdays as integers
     * represented by {@link java.util.Calendar#DAY_OF_WEEK}
     *
     * @return An integer representing the week day according to
     *         {@link java.util.Calendar#DAY_OF_WEEK}
     */
    public int getLastVisibleDayOfWeek() {
        return lastDay;
    }

    /**
     * <p>
     * This method restricts the hours that are shown per day. This affects the
     * weekly view. The general contract is that <b>firstHour < lastHour</b>.
     * </p>
     *
     * <p>
     * Note that this only affects the rendering process. Items are still
     * requested by the dates set by {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)}.
     * </p>
     * You can use {@link #autoScaleVisibleHoursOfDay()} for automatic scaling
     * of the visible hours based on current items.
     *
     * @param firstHour
     *            the first hour of the day to show, between 0 and 23
     * @see #autoScaleVisibleHoursOfDay()
     */
    public void setFirstVisibleHourOfDay(int firstHour) {
        if (this.firstHour != firstHour && firstHour >= 0 && firstHour <= 23  && firstHour <= getLastVisibleHourOfDay()) {
            this.firstHour = firstHour;
            getState().firstHourOfDay = firstHour;
        }
    }

    /**
     * Returns the first visible hour in the week view. Returns the hour using a
     * 24h time format
     *
     */
    public int getFirstVisibleHourOfDay() {
        return firstHour;
    }

    /**
     * This method restricts the hours that are shown per day. This affects the
     * weekly view. The general contract is that <b>firstHour < lastHour</b>.
     * <p>
     * Note that this only affects the rendering process. Items are still
     * requested by the dates set by {@link #setStartDate(Date)} and
     * {@link #setEndDate(Date)}.
     * <p>
     * You can use {@link #autoScaleVisibleHoursOfDay()} for automatic scaling
     * of the visible hours based on current items.
     *
     * @param lastHour
     *            the first hour of the day to show, between 0 and 23
     * @see #autoScaleVisibleHoursOfDay()
     */
    public void setLastVisibleHourOfDay(int lastHour) {
        if (this.lastHour != lastHour && lastHour >= 0 && lastHour <= 23 && lastHour >= getFirstVisibleHourOfDay()) {
            this.lastHour = lastHour;
            getState().lastHourOfDay = lastHour;
        }
    }

    /**
     * Returns the last visible hour in the week view. Returns the hour using a
     * 24h time format
     *
     */
    public int getLastVisibleHourOfDay() {
        return lastHour;
    }

    /**
     * Gets the date caption format for the weekly view.
     *
     * @return The pattern used in caption of dates in weekly view.
     */
    public String getWeeklyCaptionFormat() {
        return weeklyCaptionFormat;
    }

    /**
     * Sets custom date format for the weekly view. This is the caption of the
     * date. Format could be like "mmm MM/dd".
     *
     * @param dateFormatPattern
     *            The date caption pattern.
     */
    public void setWeeklyCaptionFormat(String dateFormatPattern) {
        if (weeklyCaptionFormat == null && dateFormatPattern != null
                || weeklyCaptionFormat != null && !weeklyCaptionFormat.equals(dateFormatPattern)) {
            weeklyCaptionFormat = dateFormatPattern;
            markAsDirty();
        }
    }

    /**
     * Sets sort order for items. By default sort order is
     * {@link CalendarState.ItemSortOrder#DURATION_DESC}.
     *
     * @param order
     *            sort strategy for items
     */
    public void setItemSortOrder(CalendarState.ItemSortOrder order) {
        if (order == null) {
            getState().itemSortOrder = CalendarState.ItemSortOrder.DURATION_DESC;
        } else {
            getState().itemSortOrder = CalendarState.ItemSortOrder.values()[order.ordinal()];
        }
    }

    /**
     * Returns sort order for items.
     *
     * @return currently active sort strategy
     */
    public CalendarState.ItemSortOrder getItemSortOrder() {
        CalendarState.ItemSortOrder order = getState(false).itemSortOrder;
        if (order == null) {
            return CalendarState.ItemSortOrder.DURATION_DESC;
        } else {
            return order;
        }
    }

    private DateFormat getWeeklyCaptionFormatter() {
        if (weeklyCaptionFormat != null) {
            return new SimpleDateFormat(weeklyCaptionFormat, getLocale());
        } else {
            return SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT, getLocale());
        }
    }

    /**
     * Get the day of week by the given calendar and its locale
     *
     * @param calendar
     *            The calendar to use
     * @return
     */
    private static int getDowByLocale(java.util.Calendar calendar) {
        int fow = calendar.get(java.util.Calendar.DAY_OF_WEEK);

        // monday first
        if (calendar.getFirstDayOfWeek() == java.util.Calendar.MONDAY) {
            fow = fow == java.util.Calendar.SUNDAY ? 7 : fow - 1;
        }

        return fow;
    }

    /**
     * Is the user allowed to trigger items which alters the items
     *
     * @return true if the client is allowed to send changes to server
     */
    protected boolean isClientChangeAllowed() {
        return isEnabled();
    }

    /**
     * Fires an event when the user selecing moving forward/backward in the
     * calendar.
     *
     * @param forward
     *            True if the calendar moved forward else backward is assumed.
     */
    protected void fireNavigationEvent(boolean forward) {
        if (forward) {
            fireEvent(new CalendarComponentEvents.ForwardEvent(this));
        } else {
            fireEvent(new CalendarComponentEvents.BackwardEvent(this));
        }
    }

    /**
     * Fires an item move event to all server side move listerners
     *
     * @param index
     *            The index of the item in the items list
     * @param newFromDatetime
     *            The changed from date time
     */
    protected void fireItemMove(int index, Date newFromDatetime) {
        CalendarComponentEvents.ItemMoveEvent event = new CalendarComponentEvents.ItemMoveEvent(this, items.get(index),
                newFromDatetime);

        if (calendarItemProvider instanceof CalendarComponentEvents.ItemMoveHandler) {
            // Notify event provider if it is an event move handler
            ((CalendarComponentEvents.ItemMoveHandler) calendarItemProvider).itemMove(event);
        }

        // Notify event move handler attached by using the
        // setHandler(ItemMoveHandler) method
        fireEvent(event);
    }

    /**
     * Fires event when a week was clicked in the calendar.
     *
     * @param week
     *            The week that was clicked
     * @param year
     *            The year of the week
     */
    protected void fireWeekClick(int week, int year) {
        fireEvent(new CalendarComponentEvents.WeekClick(this, week, year));
    }

    /**
     * Fires event when a date was clicked in the calendar. Uses an existing
     * event from the event cache.
     *
     * @param index
     *            The index of the event in the event cache.
     */
    protected void fireItemClick(Integer index) {
        fireEvent(new CalendarComponentEvents.ItemClickEvent(this, items.get(index)));
    }

    /**
     * Fires event when a date was clicked in the calendar. Creates a new event
     * for the date and passes it to the listener.
     *
     * @param date
     *            The date and time that was clicked
     */
    protected void fireDateClick(Date date) {
        fireEvent(new CalendarComponentEvents.DateClickEvent(this, date));
    }

    /**
     * Fires an event range selected event. The event is fired when a user
     * highlights an area in the calendar. The highlighted areas start and end
     * dates are returned as arguments.
     *
     * @param from
     *            The start date and time of the highlighted area
     * @param to
     *            The end date and time of the highlighted area
     */
    protected void fireRangeSelect(Date from, Date to) {
        fireEvent(new CalendarComponentEvents.RangeSelectEvent(this, from, to));
    }

    /**
     * Fires an item resize event. The event is fired when a user resizes the
     * item in the calendar causing the time range of the item to increase or
     * decrease. The new start and end times are returned as arguments to this
     * method.
     *
     * @param index
     *            The index of the item in the item cache
     * @param startTime
     *            The new start date and time of the item
     * @param endTime
     *            The new end date and time of the item
     */
    protected void fireItemResize(int index, Date startTime, Date endTime) {

        CalendarComponentEvents.ItemResizeEvent event =
                new CalendarComponentEvents.ItemResizeEvent(this, items.get(index), startTime, endTime);

        if (calendarItemProvider instanceof CalendarComponentEvents.EventResizeHandler) {
            // Notify event provider if it is an event resize handler
            ((CalendarComponentEvents.EventResizeHandler) calendarItemProvider).itemResize(event);
        }

        // Notify event resize handler attached by using the
        // setHandler(ItemMoveHandler) method
        fireEvent(event);
    }

    /**
     * Localized display names for week days starting from sunday. Returned
     * array's length is always 7.
     *
     * @return Array of localized weekday names.
     */
    protected String[] getDayNamesShort() {
        DateFormatSymbols s = new DateFormatSymbols(getLocale());
        return Arrays.copyOfRange(s.getWeekdays(), 1, 8);
    }

    /**
     * Localized display names for months starting from January. Returned
     * array's length is always 12.
     *
     * @return Array of localized month names.
     */
    protected String[] getMonthNamesShort() {
        DateFormatSymbols s = new DateFormatSymbols(getLocale());
        return Arrays.copyOf(s.getShortMonths(), 12);
    }

    /**
     * Gets a date that is first day in the week that target given date belongs
     * to.
     *
     * @param date
     *            Target date
     * @return Date that is first date in same week that given date is.
     */
    protected Date getFirstDateForWeek(Date date) {
        int firstDayOfWeek = currentCalendar.getFirstDayOfWeek();

        currentCalendar.setTime(date);
        while (firstDayOfWeek != currentCalendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            currentCalendar.add(java.util.Calendar.DATE, -1);
        }

        return currentCalendar.getTime();
    }

    /**
     * Gets a date that is last day in the week that target given date belongs
     * to.
     *
     * @param date
     *            Target date
     * @return Date that is last date in same week that given date is.
     */
    protected Date getLastDateForWeek(Date date) {

        currentCalendar.setTime(date);
        currentCalendar.add(java.util.Calendar.DATE, 1);

        int firstDayOfWeek = currentCalendar.getFirstDayOfWeek();

        // Roll to weeks last day using firstdayofweek. Roll until FDofW is
        // found and then roll back one day.
        while (firstDayOfWeek != currentCalendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            currentCalendar.add(java.util.Calendar.DATE, 1);
        }

        currentCalendar.add(java.util.Calendar.DATE, -1);

        return currentCalendar.getTime();
    }

    /**
     * Calculates the end time of the day using the given calendar and date
     *
     * @param date
     * @param calendar
     *            the calendar instance to be used in the calculation. The given
     *            instance is unchanged in this operation.
     * @return the given date, with time set to the end of the day
     */
    private static Date getEndOfDay(java.util.Calendar calendar, Date date) {
        java.util.Calendar calendarClone = (java.util.Calendar) calendar.clone();

        calendarClone.setTime(date);
        calendarClone.set(java.util.Calendar.MILLISECOND,
                calendarClone.getActualMaximum(java.util.Calendar.MILLISECOND));
        calendarClone.set(java.util.Calendar.SECOND,
                calendarClone.getActualMaximum(java.util.Calendar.SECOND));
        calendarClone.set(java.util.Calendar.MINUTE,
                calendarClone.getActualMaximum(java.util.Calendar.MINUTE));
        calendarClone.set(java.util.Calendar.HOUR,
                calendarClone.getActualMaximum(java.util.Calendar.HOUR));
        calendarClone.set(java.util.Calendar.HOUR_OF_DAY,
                calendarClone.getActualMaximum(java.util.Calendar.HOUR_OF_DAY));

        return calendarClone.getTime();
    }

    /**
     * Calculates the end time of the day using the given calendar and date
     *
     * @param date
     * @param calendar
     *            the calendar instance to be used in the calculation. The given
     *            instance is unchanged in this operation.
     * @return the given date, with time set to the end of the day
     */
    private static Date getStartOfDay(java.util.Calendar calendar, Date date) {
        java.util.Calendar calendarClone = (java.util.Calendar) calendar.clone();

        calendarClone.setTime(date);
        calendarClone.set(java.util.Calendar.MILLISECOND, 0);
        calendarClone.set(java.util.Calendar.SECOND, 0);
        calendarClone.set(java.util.Calendar.MINUTE, 0);
        calendarClone.set(java.util.Calendar.HOUR, 0);
        calendarClone.set(java.util.Calendar.HOUR_OF_DAY, 0);

        return calendarClone.getTime();
    }

    /**
     * Finds the first day of the week and returns a day representing the start
     * of that day
     *
     * @param start
     *            The actual date
     * @param expandToFullWeek
     *            Should the returned date be moved to the start of the week
     * @return If expandToFullWeek is set then it returns the first day of the
     *         week, else it returns a clone of the actual date with the time
     *         set to the start of the day
     */
    protected Date expandStartDate(Date start, boolean expandToFullWeek) {
        // If the duration is more than week, use monthly view and get startweek
        // and endweek. Example if views daterange is from tuesday to next weeks
        // wednesday->expand to monday to nextweeks sunday. If firstdayofweek =
        // monday
        if (expandToFullWeek) {
            start = getFirstDateForWeek(start);

        } else {
            start = (Date) start.clone();
        }

        // Always expand to the start of the first day to the end of the last
        // day
        start = getStartOfDay(currentCalendar, start);

        return start;
    }

    /**
     * Finds the last day of the week and returns a day representing the end of
     * that day
     *
     * @param end
     *            The actual date
     * @param expandToFullWeek
     *            Should the returned date be moved to the end of the week
     * @return If expandToFullWeek is set then it returns the last day of the
     *         week, else it returns a clone of the actual date with the time
     *         set to the end of the day
     */
    protected Date expandEndDate(Date end, boolean expandToFullWeek) {

        // If the duration is more than week, use monthly view and get startweek and endweek.
        // Example: if views daterange is from tuesday to next weeks
        // wednesday -> expand to monday to nextweeks sunday.
        // If firstdayofweek = monday

        if (expandToFullWeek) {
            end = getLastDateForWeek(end);
        } else {
            end = (Date) end.clone();
        }

        // Always expand to the start of the first day to the end of the last day
        end = getEndOfDay(currentCalendar, end);
        return end;
    }

    /**
     * Set the {@link CalendarItemProvider} to be used with this calendar. The
     * DataProvider is used to query for items to show, and must be non-null.
     * By default a {@link BasicItemProvider} is used.
     *
     * @param calendarItemProvider
     *            the calendarItemProvider to set. Cannot be null.
     */
    public void setDataProvider(CalendarItemProvider<ITEM> calendarItemProvider) {

        if (calendarItemProvider == null) {
            throw new IllegalArgumentException(
                    "Calendar event provider cannot be null");
        }

        // remove old listener
        if (getDataProvider() instanceof CalendarItemProvider.ItemSetChangedNotifier) {
            ((ItemSetChangedNotifier) getDataProvider()).removeItemSetChangedListener(this);
        }

        this.calendarItemProvider = calendarItemProvider;

        // add new listener
        if (calendarItemProvider instanceof CalendarItemProvider.ItemSetChangedNotifier) {
            ((ItemSetChangedNotifier) calendarItemProvider).addItemSetChangedListener(this);
        }
    }

    /**
     * @return the {@link CalendarItemProvider} currently used
     */
    public CalendarItemProvider<ITEM> getDataProvider() {
        return calendarItemProvider;
    }

    @Override
    public void itemSetChanged(ItemSetChangedEvent changeEvent) {
        // sanity check
        if (calendarItemProvider == changeEvent.getProvider()) {
            markAsDirty();
        }
    }

    /**
     * Set the handler for the given type information. Mirrors
     * {@link #addListener(String, Class, Object, Method) addListener} from
     * AbstractComponent
     *
     * @param eventId
     *            A unique id for the event. Usually one of
     *            {@link CalendarEventId}
     * @param eventType
     *            The class of the event, most likely a subclass of
     *            {@link CalendarComponentEvent}
     * @param listener
     *            A listener that listens to the given event
     * @param listenerMethod
     *            The method on the lister to call when the event is triggered
     */
    protected void setHandler(String eventId, Class<?> eventType,
            EventListener listener, Method listenerMethod) {
        if (handlers.get(eventId) != null) {
            removeListener(eventId, eventType, handlers.get(eventId));
            handlers.remove(eventId);
        }

        if (listener != null) {
            addListener(eventId, eventType, listener, listenerMethod);
            handlers.put(eventId, listener);
        }
    }

    @Override
    public void setHandler(CalendarComponentEvents.ForwardHandler listener) {
        setHandler(CalendarComponentEvents.ForwardEvent.EVENT_ID, CalendarComponentEvents.ForwardEvent.class, listener,
                CalendarComponentEvents.ForwardHandler.forwardMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.BackwardHandler listener) {
        setHandler(CalendarComponentEvents.BackwardEvent.EVENT_ID, CalendarComponentEvents.BackwardEvent.class, listener,
                CalendarComponentEvents.BackwardHandler.backwardMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.DateClickHandler listener) {
        setHandler(CalendarComponentEvents.DateClickEvent.EVENT_ID, CalendarComponentEvents.DateClickEvent.class, listener,
                CalendarComponentEvents.DateClickHandler.dateClickMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.ItemClickHandler listener) {
        setHandler(CalendarComponentEvents.ItemClickEvent.EVENT_ID, CalendarComponentEvents.ItemClickEvent.class, listener,
                CalendarComponentEvents.ItemClickHandler.itemClickMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.WeekClickHandler listener) {
        setHandler(CalendarComponentEvents.WeekClick.EVENT_ID, CalendarComponentEvents.WeekClick.class, listener,
                CalendarComponentEvents.WeekClickHandler.weekClickMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.EventResizeHandler listener) {
        setHandler(CalendarComponentEvents.ItemResizeEvent.EVENT_ID, CalendarComponentEvents.ItemResizeEvent.class, listener,
                CalendarComponentEvents.EventResizeHandler.itemResizeMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.RangeSelectHandler listener) {
        setHandler(CalendarComponentEvents.RangeSelectEvent.EVENT_ID, CalendarComponentEvents.RangeSelectEvent.class, listener,
                CalendarComponentEvents.RangeSelectHandler.rangeSelectMethod);
    }

    @Override
    public void setHandler(CalendarComponentEvents.ItemMoveHandler listener) {
        setHandler(CalendarComponentEvents.ItemMoveEvent.EVENT_ID, CalendarComponentEvents.ItemMoveEvent.class, listener,
                CalendarComponentEvents.ItemMoveHandler.itemMoveMethod);
    }

    @Override
    public EventListener getHandler(String eventId) {
        return handlers.get(eventId);
    }

    /**
     * Get the currently active drop handler
     */
    @Override
    public DropHandler getDropHandler() {
        return dropHandler;
    }

    /**
     * Set the drop handler for the calendar See {@link DropHandler} for
     * implementation details.
     *
     * @param dropHandler
     *            The drop handler to set
     */
    public void setDropHandler(DropHandler dropHandler) {
        this.dropHandler = dropHandler;
    }

    @Override
    public TargetDetails translateDropTargetDetails(Map<String, Object> clientVariables) {
        Map<String, Object> serverVariables = new HashMap<>();

        if (clientVariables.containsKey("dropSlotIndex")) {
            int slotIndex = (Integer) clientVariables.get("dropSlotIndex");
            int dayIndex = (Integer) clientVariables.get("dropDayIndex");

            currentCalendar.setTime(getStartOfDay(currentCalendar, startDate));
            currentCalendar.add(java.util.Calendar.DATE, dayIndex);

            // change this if slot length is modified
            currentCalendar.add(java.util.Calendar.MINUTE, slotIndex * 30);

            serverVariables.put("dropTime", currentCalendar.getTime());

        } else {
            int dayIndex = (Integer) clientVariables.get("dropDayIndex");
            currentCalendar.setTime(expandStartDate(startDate, true));
            currentCalendar.add(java.util.Calendar.DATE, dayIndex);
            serverVariables.put("dropDay", currentCalendar.getTime());
        }
        serverVariables.put("mouseEvent", clientVariables.get("mouseEvent"));

        CalendarTargetDetails td = new CalendarTargetDetails(serverVariables, this);
        td.setHasDropTime(clientVariables.containsKey("dropSlotIndex"));

        return td;
    }

    @Override
    public List<ITEM> getItems(Date startDate, Date endDate) {
        List<ITEM> events = getDataProvider().getItems(startDate, endDate);
        cacheMinMaxTimeOfDay(events);
        return events;
    }

    /**
     * Adds an action handler to the calendar that handles event produced by the
     * context menu.
     *
     * <p>
     * The {@link Handler#getActions(Object, Object)} parameters depend on what
     * view the Calendar is in:
     * <ul>
     * <li>If the Calendar is in <i>Day or Week View</i> then the target
     * parameter will be a {@link CalendarDateRange} with a range of
     * half-an-hour. The {@link Handler#getActions(Object, Object)} method will
     * be called once per half-hour slot.</li>
     * <li>If the Calendar is in <i>Month View</i> then the target parameter
     * will be a {@link CalendarDateRange} with a range of one day. The
     * {@link Handler#getActions(Object, Object)} will be called once for each
     * day.
     * </ul>
     * The Dates passed into the {@link CalendarDateRange} are in the same
     * timezone as the calendar is.
     * </p>
     *
     * <p>
     * The {@link Handler#handleAction(Action, Object, Object)} parameters
     * depend on what the context menu is called upon:
     * <ul>
     * <li>If the context menu is called upon an item then the target parameter
     * is the item, i.e. instanceof {@link CalendarItem}</li>
     * <li>If the context menu is called upon an empty slot then the target is a
     * {@link Date} representing that slot
     * </ul>
     * </p>
     */
    @Override
    public void addActionHandler(Handler actionHandler) {
        if (actionHandler != null) {
            if (actionHandlers == null) {
                actionHandlers = new LinkedList<>();
                actionMapper = new KeyMapper<>();
            }

            if (!actionHandlers.contains(actionHandler)) {
                actionHandlers.add(actionHandler);
                markAsDirty();
            }
        }
    }

    /**
     * Is the calendar in a mode where all days of the month is shown
     *
     * @return Returns true if calendar is in monthly mode and false if it is in
     *         weekly mode
     */
    public boolean isMonthlyMode() {
        CalendarState state = getState(false);
        return state.days == null || state.days.size() > 7;
    }

    /**
     * Is the calendar in a mode where one day of the month is shown
     *
     * @return Returns true if calendar is in day mode and false if it is in
     *         weekly mode
     */
    public boolean isDayMode() {
        CalendarState state = getState(false);
        return state.days == null || state.days.size() == 1;
    }

    /**
     * Is the calendar in a mode where two day or max 7 days of the month is shown
     *
     * @return Returns true if calendar is in weekly mode and false if not
     */
    public boolean isWeeklyMode() {
        return !isDayMode() && !isMonthlyMode();
    }

    @Override
    public void removeActionHandler(Handler actionHandler) {
        if (actionHandlers != null && actionHandlers.contains(actionHandler)) {
            actionHandlers.remove(actionHandler);
            if (actionHandlers.isEmpty()) {
                actionHandlers = null;
                actionMapper = null;
            }
            markAsDirty();
        }
    }

    private class CalendarServerRpcImpl implements CalendarServerRpc {

        @Override
        public void itemMove(int itemIndex, String newDate) {

            if (!isClientChangeAllowed()) {
                return;
            }

            if (newDate != null) {
                try {
                    Date d = df_date_time.parse(newDate);
                    if (itemIndex >= 0 && itemIndex < items.size()
                            && items.get(itemIndex) != null) {
                        fireItemMove(itemIndex, d);
                    }
                } catch (ParseException e) {
                    getLogger().log(Level.WARNING, e.getMessage());
                }
            }
        }

        @Override
        public void rangeSelect(String range) {

            if (!isClientChangeAllowed()) {
                return;
            }

            // two dates transmitted
            if (range != null && range.length() > 14 && range.contains("TO")) {

                String[] dates = range.split("TO");
                try {

                    fireRangeSelect(
                            df_date.parse(dates[0]),
                            df_date.parse(dates[1]));

                } catch (ParseException e) {
                    // NOP
                }

            } else

                // A date with start time and end time transmitted
                if (range != null && range.length() > 12 && range.contains(":")) {

                String[] dates = range.split(":");
                if (dates.length == 3) {
                    try {

                        currentCalendar.setTime(df_date.parse(dates[0]));

                        int startMinutes = Integer.parseInt(dates[1]);
                        int endMinutes = Integer.parseInt(dates[2]);

                        currentCalendar.add(java.util.Calendar.MINUTE, startMinutes);
                        Date start = currentCalendar.getTime();

                        currentCalendar.add(java.util.Calendar.MINUTE, endMinutes - startMinutes);
                        Date end = currentCalendar.getTime();

                        fireRangeSelect(start, end);

                    } catch (ParseException | NumberFormatException e) {
                        // NOP
                    }
                }
            }
        }

        @Override
        public void forward() {
            fireEvent(new CalendarComponentEvents.ForwardEvent(Calendar.this));
        }

        @Override
        public void backward() {
            fireEvent(new CalendarComponentEvents.BackwardEvent(Calendar.this));
        }

        @Override
        public void dateClick(String date) {
            if (date != null && date.length() > 6) {
                try {
                    fireDateClick(df_date.parse(date));
                } catch (ParseException e) {
                    // NOP
                }
            }
        }

        @Override
        public void weekClick(String eventValue) {
            if (eventValue.length() > 0 && eventValue.contains("w")) {
                String[] splitted = eventValue.split("w");
                if (splitted.length == 2) {
                    try {
                        int yr = Integer.parseInt(splitted[0]);
                        int week = Integer.parseInt(splitted[1]);
                        fireWeekClick(week, yr);
                    } catch (NumberFormatException e) {
                        // NOP
                    }
                }
            }
        }

        @Override
        public void itemClick(int itemIndex) {
            if (itemIndex >= 0 && itemIndex < items.size()
                    && items.get(itemIndex) != null) {
                fireItemClick(itemIndex);
            }
        }

        @Override
        public void itemResize(int itemIndex, String newStartDate, String newEndDate) {

            if (!isClientChangeAllowed()) {
                return;
            }

            if (newStartDate != null && !"".equals(newStartDate)
                    && newEndDate != null && !"".equals(newEndDate)) {

                try {
                    Date newStartTime = df_date_time.parse(newStartDate);
                    Date newEndTime = df_date_time.parse(newEndDate);

                    fireItemResize(itemIndex, newStartTime, newEndTime);
                } catch (ParseException e) {
                    // NOOP
                }
            }
        }

        @Override
        public void scroll(int scrollPosition) {
            scrollTop = scrollPosition;
            markAsDirty();
        }

        @Override
        public void actionOnEmptyCell(String actionKey, String startDate, String endDate) {

            Action action = actionMapper.get(actionKey);
            SimpleDateFormat formatter = new SimpleDateFormat(DateConstants.ACTION_DATE_FORMAT_PATTERN);
            formatter.setTimeZone(getTimeZone());

            try {
                Date start = formatter.parse(startDate);
                for (Action.Handler ah : actionHandlers) {
                    ah.handleAction(action, Calendar.this, start);
                }

            } catch (ParseException e) {
                getLogger().log(Level.WARNING,
                        "Could not parse action date string");
            }

        }

        @Override
        public void actionOnItem(String actionKey, String startDate, String endDate, int itemIndex) {

            Action action = actionMapper.get(actionKey);
            SimpleDateFormat formatter = new SimpleDateFormat(DateConstants.ACTION_DATE_FORMAT_PATTERN);
            formatter.setTimeZone(getTimeZone());

            for (Action.Handler ah : actionHandlers) {
                ah.handleAction(action, Calendar.this, items.get(itemIndex));
            }
        }
    }

    @Override
    public void changeVariables(Object source, Map<String, Object> variables) {
        /*
         * Only defined to fulfill the LegacyComponent interface used for
         * calendar drag & drop. No implementation required.
         */
    }

    @Override
    public void paintContent(PaintTarget target) throws PaintException {
        if (dropHandler != null) {
            dropHandler.getAcceptCriterion().paint(target);
        }
    }

    /**
     * Sets whether the item captions are rendered as HTML.
     * <p>
     * If set to true, the captions are rendered in the browser as HTML and the
     * developer is responsible for ensuring no harmful HTML is used. If set to
     * false, the caption is rendered in the browser as plain text.
     * <p>
     * The default is false, i.e. to render that caption as plain text.
     *
     * @param itemCaptionAsHtml
     *            true if the captions are rendered as HTML, false if rendered
     *            as plain text
     */
    public void setItemCaptionAsHtml(boolean itemCaptionAsHtml) {
        getState().itemCaptionAsHtml = itemCaptionAsHtml;
    }

    /**
     * Checks whether event captions are rendered as HTML
     * <p>
     * The default is false, i.e. to render that caption as plain text.
     *
     * @return true if the captions are rendered as HTML, false if rendered as
     *         plain text
     */
    public boolean isItemCaptionAsHtml() {
        return getState(false).itemCaptionAsHtml;
    }

    @Override
    public void readDesign(Element design, DesignContext designContext) {
        super.readDesign(design, designContext);

        Attributes attr = design.attributes();
        if (design.hasAttr("time-format")) {
            setTimeFormat(TimeFormat.valueOf(
                    "Format" + design.attr("time-format").toUpperCase()));
        }

        if (design.hasAttr("start-date")) {
            setStartDate(DesignAttributeHandler.readAttribute("start-date",
                    attr, Date.class));
        }
        if (design.hasAttr("end-date")) {
            setEndDate(DesignAttributeHandler.readAttribute("end-date", attr,
                    Date.class));
        }
    };

    @Override
    public void writeDesign(Element design, DesignContext designContext) {
        super.writeDesign(design, designContext);

        if (currentTimeFormat != null) {
            design.attr("time-format",
                    currentTimeFormat == TimeFormat.Format12H ? "12h" : "24h");
        }
        if (startDate != null) {
            design.attr("start-date", df_date.format(getStartDate()));
        }
        if (endDate != null) {
            design.attr("end-date", df_date.format(getEndDate()));
        }
        if (!getTimeZone().equals(TimeZone.getDefault())) {
            design.attr("time-zone", getTimeZone().getID());
        }
    }

    @Override
    protected Collection<String> getCustomAttributes() {
        Collection<String> customAttributes = super.getCustomAttributes();
        customAttributes.add("time-format");
        customAttributes.add("start-date");
        customAttributes.add("end-date");
        return customAttributes;
    }

    /**
     * Allow setting first day of week independent of Locale. Set to null if you
     * want first day of week being defined by the locale
     *
     * @since 7.6
     * @param dayOfWeek
     *            any of java.util.Calendar.SUNDAY..java.util.Calendar.SATURDAY
     *            or null to revert to default first day of week by locale
     */
    public void setFirstDayOfWeek(Integer dayOfWeek) {

        int minimalSupported = java.util.Calendar.SUNDAY;
        int maximalSupported = java.util.Calendar.SATURDAY;

        if (dayOfWeek != null && (dayOfWeek < minimalSupported || dayOfWeek > maximalSupported)) {
            throw new IllegalArgumentException(String.format(
                    "Day of week must be between %s and %s. Actually received: %s",
                    minimalSupported, maximalSupported, dayOfWeek));
        }

        customFirstDayOfWeek = dayOfWeek;
        markAsDirty();
    }

    /**
     * Add a time block start index. Time steps are half hour beginning at 0
     * and a minimal time slot length of 1800000 milliseconds is used.
     *
     * @param day Day for this time slot
     * @param fromMillies time millies from where the block starts
     * @param styleName css class for this block (currently unused)
     */
    protected final void addTimeBlockInternaly(Date day, Long fromMillies, String styleName) {
        Set<Long> times;
        if (blockedTimes.containsKey(day)) {
            times = blockedTimes.get(day);
        } else {
            times = new HashSet<>();
        }
        times.add(fromMillies);
        blockedTimes.put(day, times);
    }

    /**
     * Add a time block marker for a range of time. Time steps are half hour,
     * so a minimal time slot is 1800000 milliseconds long.
     *
     * @param fromMillies time millies from where the block starts
     * @param toMillies time millies from where the block ends
     */
    public void addTimeBlock(long fromMillies, long toMillies) {
        addTimeBlock(fromMillies, toMillies, "");
    }

    /**
     * Add a time block marker for a range of time. Time steps are half hour,
     * so a minimal time slot is 1800000 milliseconds long.
     *
     * @param fromMillies time millies from where the block starts
     * @param toMillies time millies from where the block ends
     * @param styleName css class for this block (currently unused)
     */
    public void addTimeBlock(long fromMillies, long toMillies, String styleName) {
        addTimeBlock(allOverDate, fromMillies, toMillies, styleName);
    }

    /**
     * Add a time block marker for a range of time. Time steps are half hour,
     * so a minimal time slot is 1800000 milliseconds long.
     *
     * @param day Day for this time slot
     * @param fromMillies time millies from where the block starts
     * @param toMillies time millies from where the block ends
     */
    public void addTimeBlock(Date day, long fromMillies, long toMillies) {
        addTimeBlock(day, fromMillies, toMillies, "");
    }

    /**
     * Add a time block marker for a range of time. Time steps are half hour,
     * so a minimal time slot is 1800000 milliseconds long.
     *
     * @param day Day for this time slot
     * @param fromMillies time millies from where the block starts
     * @param toMillies time millies from where the block ends
     * @param styleName css class for this block (currently unused)
     */
    public void addTimeBlock(Date day, long fromMillies, long toMillies, String styleName) {
        assert (toMillies > fromMillies && fromMillies % 1800000 == 0 && toMillies % 1800000 == 0);

        while (fromMillies < toMillies) {

            addTimeBlockInternaly(day, fromMillies, styleName);
            fromMillies += 1800000;
        }

        markAsDirty();
    }

    public void clearBlockedTimes() {
        blockedTimes.clear();
        markAsDirty();
    }

    public void clearBlockedTimes(Date day) {
        if (blockedTimes.containsKey(day)) {
            blockedTimes.remove(day);
        }
        markAsDirty();
    }

}