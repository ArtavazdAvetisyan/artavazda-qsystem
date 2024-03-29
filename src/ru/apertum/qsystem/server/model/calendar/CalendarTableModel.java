/*
 *  Copyright (C) 2010 {Apertum}Projects. web: www.apertum.ru email: info@apertum.ru
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ru.apertum.qsystem.server.model.calendar;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Property;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import ru.apertum.qsystem.common.QLog;
import ru.apertum.qsystem.common.exceptions.ClientException;
import ru.apertum.qsystem.server.Spring;

/**
 * Модель для отображения сетки календаля
 * @author Evgeniy Egorov
 */
public class CalendarTableModel extends AbstractTableModel {

    private final List<FreeDay> days;
    private List<FreeDay> days_del;

    public CalendarTableModel(long calcId) {
        QLog.l().logger().debug("Создаем модель для календаря");
        this.calcId = calcId;
        days = getFreeDays(calcId);
        days_del = new ArrayList<>(days);
    }
    /**
     * В каком календаре сейчас работаем
     */
    final private long calcId;

    /**
     * Выборка из БД требуемых данных.
     * @param calcId id календаря
     * @return список выходных дней определенного календаря
     */
    public synchronized static LinkedList<FreeDay> getFreeDays(final Long calcId) {
        final DetachedCriteria criteria = DetachedCriteria.forClass(FreeDay.class);
        criteria.add(Property.forName("calendarId").eq(calcId));
        return new LinkedList<>(Spring.getInstance().getHt().findByCriteria(criteria));
    }

    /**
     * Добавляем дату. Если Такая уже есть то инвертируем
     * @param date
     * @param noInvert true - при обнаружении выходного оставлять его выходным
     * @return Добавлена как свободная или как рабочая/ true = свободная
     */
    public boolean addDay(Date date, boolean noInvert) {
        final FreeDay day = isFreeDate(date);
        if (day != null) {
            if (noInvert) {
                return true;
            } else {
                days.remove(day);
            }
            return false;
        } else {
            days.add(new FreeDay(date, calcId));
            return true;
        }
    }

    /**
     * Проверяем добавлена ли в выходные уже
     * @param date
     * @return
     */
    public FreeDay isFreeDate(Date date) {
        for (FreeDay day : days) {
            if (day.equals(date)) {
                return day;
            }
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return 12;
    }

    @Override
    public int getColumnCount() {
        return 32;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return "X";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? super.getColumnClass(columnIndex) : FreeDay.class;
    }

    @Override
    public String getColumnName(int column) {
        return column == 0 ? "" : Integer.toString(column);
    }

    /**
     * Сбросить выделенные дни в календаре
     */
    public void dropCalendar() {
        QLog.l().logger().debug("Сбросим календарь");
        days.clear();
        fireTableDataChanged();
    }

    /**
     * Пометить все субботы выходными
     */
    public void checkSaturday() {
        QLog.l().logger().debug("Пометив все субботы");
        final GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(new Date());
        final int ye = gc.get(GregorianCalendar.YEAR) % 4 == 0 ? 366 : 365;
        for (int d = 1; d <= ye; d++) {
            gc.set(GregorianCalendar.DAY_OF_YEAR, d);
            if (gc.get(GregorianCalendar.DAY_OF_WEEK) == 7) {
                addDay(gc.getTime(), true);
            }
        }
        fireTableDataChanged();
    }

    /**
     * Пометить все воскресенья выходными
     */
    public void checkSunday() {
        QLog.l().logger().debug("Пометим все воскресенья");
        final GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(new Date());
        final int ye = gc.get(GregorianCalendar.YEAR) % 4 == 0 ? 366 : 365;
        for (int d = 1; d <= ye; d++) {
            gc.set(GregorianCalendar.DAY_OF_YEAR, d);
            if (gc.get(GregorianCalendar.DAY_OF_WEEK) == 1) {
                addDay(gc.getTime(), true);
            }
        }
        fireTableDataChanged();
    }

    /**
     * Сохранить календарь.
     */
    public void save() {
        QLog.l().logger().info("Сохраняем календарь ID = " + calcId);

        final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("SomeTxName");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = Spring.getInstance().getTxManager().getTransaction(def);
        try {
            final LinkedList<FreeDay> dels = new LinkedList<>();
            for (FreeDay bad : days_del) {
                boolean f = true;
                for (FreeDay good : days) {
                    if (good.equals(bad)) {
                        f = false;
                    }
                }
                if (f) {
                    dels.add(bad);
                }
            }
            Spring.getInstance().getHt().deleteAll(dels);
            Spring.getInstance().getHt().saveOrUpdateAll(days);
        } catch (Exception ex) {
            Spring.getInstance().getTxManager().rollback(status);
            throw new ClientException("Ошибка выполнения операции изменения данных в БД(JDBC).\nВозможно Вы добавили новый календарь, изменили его, пытаетесь сохранить содержимое календаря, но общую конфигурацию не сохранили.\nСохраните всю конфигурацию(Ctrl + S) и еще раз попытайтесь сохранить содержимое календаря.\n\n[" + ex.getLocalizedMessage() + "]\n(" + ex.toString() + ")");
        }
        Spring.getInstance().getTxManager().commit(status);
        QLog.l().logger().debug("Сохранили новый календарь");
        //типо чтоб были актуальные внутренние данные
        days_del = new ArrayList<>(days);
    }

    /**
     * Проверка на сохраненность календаря
     * @return
     */
    public boolean isSaved() {
        for (FreeDay day : days) {
            if (day.getId() == null) {
                return false;
            }
        }
        if (days_del.size() != days.size()) {
            return false;
        }
        return true;
    }
}
