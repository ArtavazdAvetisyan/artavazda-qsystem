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
package ru.apertum.qsystem.server.model.schedule;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import ru.apertum.qsystem.common.exceptions.ServerException;
import ru.apertum.qsystem.server.model.IidGetter;

/**
 * Класс плана для расписания.
 * @author Evgeniy Egorov
 */
@Entity
@Table(name = "schedule")
public class QSchedule implements IidGetter, Serializable {

    public QSchedule() {
    }
    @Id
    @Column(name = "id")
    //@GeneratedValue(strategy = GenerationType.AUTO)
    private Long id = new Date().getTime();

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null){
            return false;
        }
        if (!(o instanceof QSchedule)){
            throw new TypeNotPresentException("Неправильный тип для сравнения", new ServerException("Неправильный тип для сравнения"));
        }
        return id.equals(((QSchedule)o).id);
    }

    @Override
    public int hashCode() {
        return (int) (this.id != null ? this.id : 0);
    }



    public void setId(Long id) {
        this.id = id;
    }
    /**
     * Наименование плана.
     */
    @Column(name = "name")
    private String name;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
    /**
     * Тип плана
     * 0 - недельный
     * 1 - четные/нечетные дни
     */
    @Column(name = "type")
    private Integer type;

    /**
     * Тип плана
     * 0 - недельный
     * 1 - четные/нечетные дни
     * @return Тип плана
     */
    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
    /**
     * Время начала работы в первый день недели или в нечетный день, зависит от type
     */
    @Column(name = "time_begin_1")
    @Temporal(TemporalType.TIME)
    private Date time_begin_1;

    public Date getTime_begin_1() {
        return time_begin_1;
    }

    public void setTime_begin_1(Date time_begin_1) {
        this.time_begin_1 = time_begin_1;
    }
    /**
     * Время завершения работы в первый день недели или в нечетный день, зависит от type
     */
    @Column(name = "time_end_1")
    @Temporal(TemporalType.TIME)
    private Date time_end_1;

    public Date getTime_end_1() {
        return time_end_1;
    }

    public void setTime_end_1(Date time_end_1) {
        this.time_end_1 = time_end_1;
    }
    @Column(name = "time_begin_2")
    @Temporal(TemporalType.TIME)
    private Date time_begin_2;

    public Date getTime_begin_2() {
        return time_begin_2;
    }

    public void setTime_begin_2(Date time_begin_2) {
        this.time_begin_2 = time_begin_2;
    }
    @Column(name = "time_end_2")
    @Temporal(TemporalType.TIME)
    private Date time_end_2;

    public Date getTime_end_2() {
        return time_end_2;
    }

    public void setTime_end_2(Date time_end_2) {
        this.time_end_2 = time_end_2;
    }
    @Column(name = "time_begin_3")
    @Temporal(TemporalType.TIME)
    private Date time_begin_3;

    public Date getTime_begin_3() {
        return time_begin_3;
    }

    public void setTime_begin_3(Date time_begin_3) {
        this.time_begin_3 = time_begin_3;
    }
    @Column(name = "time_end_3")
    @Temporal(TemporalType.TIME)
    private Date time_end_3;

    public Date getTime_end_3() {
        return time_end_3;
    }

    public void setTime_end_3(Date time_end_3) {
        this.time_end_3 = time_end_3;
    }
    @Column(name = "time_begin_4")
    @Temporal(TemporalType.TIME)
    private Date time_begin_4;

    public Date getTime_begin_4() {
        return time_begin_4;
    }

    public void setTime_begin_4(Date time_begin_4) {
        this.time_begin_4 = time_begin_4;
    }
    @Column(name = "time_end_4")
    @Temporal(TemporalType.TIME)
    private Date time_end_4;

    public Date getTime_end_4() {
        return time_end_4;
    }

    public void setTime_end_4(Date time_end_4) {
        this.time_end_4 = time_end_4;
    }
    @Column(name = "time_begin_5")
    @Temporal(TemporalType.TIME)
    private Date time_begin_5;

    public Date getTime_begin_5() {
        return time_begin_5;
    }

    public void setTime_begin_5(Date time_begin_5) {
        this.time_begin_5 = time_begin_5;
    }
    @Column(name = "time_end_5")
    @Temporal(TemporalType.TIME)
    private Date time_end_5;

    public Date getTime_end_5() {
        return time_end_5;
    }

    public void setTime_end_5(Date time_end_5) {
        this.time_end_5 = time_end_5;
    }
    @Column(name = "time_begin_6")
    @Temporal(TemporalType.TIME)
    private Date time_begin_6;

    public Date getTime_begin_6() {
        return time_begin_6;
    }

    public void setTime_begin_6(Date time_begin_6) {
        this.time_begin_6 = time_begin_6;
    }
    @Column(name = "time_end_6")
    @Temporal(TemporalType.TIME)
    private Date time_end_6;

    public Date getTime_end_6() {
        return time_end_6;
    }

    public void setTime_end_6(Date time_end_6) {
        this.time_end_6 = time_end_6;
    }
    @Column(name = "time_begin_7")
    @Temporal(TemporalType.TIME)
    private Date time_begin_7;

    public Date getTime_begin_7() {
        return time_begin_7;
    }

    public void setTime_begin_7(Date time_begin_7) {
        this.time_begin_7 = time_begin_7;
    }
    @Column(name = "time_end_7")
    @Temporal(TemporalType.TIME)
    private Date time_end_7;

    public Date getTime_end_7() {
        return time_end_7;
    }

    public void setTime_end_7(Date time_end_7) {
        this.time_end_7 = time_end_7;
    }
}
