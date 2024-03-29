/*
 *  Copyright (C) 2011 egorov
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
package ru.apertum.qsystem.server;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.orm.hibernate3.HibernateTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.apertum.qsystem.common.exceptions.ServerException;

/**
 *
 * @author egorov
 */
public class Spring {

    private final BeanFactory factory;
    private final String driverClassName;
    private final String url;
    private final String username;
    private final String password;

    public String getDriverClassName() {
        return driverClassName;
    }

    public BeanFactory getFactory() {
        return factory;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Для выборки данных из БД
     * @return
     */
    public HibernateTemplate getHt() {
        return (HibernateTemplate) factory.getBean("hibernateTemplate");
    }

    /**
     * Для транзакций через передачу на выполнениее класса с методом где реализована работа с БД
     * @return
     */
    public TransactionTemplate getTt() {
        return new TransactionTemplate(getTxManager());
    }

    /**
     * Для транзакций обычным образом с открытием именованной транзакции
     * @return
     */
    public HibernateTransactionManager getTxManager() {
        return (HibernateTransactionManager) factory.getBean("transactionManager");
    }

    private Spring() {
        try {
            factory = new ClassPathXmlApplicationContext("/ru/apertum/qsystem/spring/qsContext.xml");
        } catch (BeanCreationException ex) {
            throw new ServerException("Ошибка создания класса-бина контекста приложения: \"" + ex.getCause().getMessage() + "\"\n"
                    + "Бин с ошибкой \"" + ex.getBeanName() + "\""
                    + "Сообщение об ошибке: \"" + ex.getCause().getMessage() + "\"\n" + ex);
        } catch (BeansException ex) {
            throw new ServerException("Ошибка класса-бина контекста приложения: \"" + ex.getCause().getMessage() + "\"\n"
                    + "Сообщение об ошибке: \"" + ex.getCause().getMessage() + "\"\n" + ex);
        } catch (Exception ex) {
            throw new ServerException("Ошибка создания контекста приложения: " + ex);
        }
        //sessionFactory = factory.getBean("mySessionFactory", SessionFactoryImpl.class);
        //ht = new HibernateTemplate(sessionFactory);

        final ComboPooledDataSource bds = (ComboPooledDataSource) factory.getBean("c3p0DataSource");
        driverClassName = bds.getDriverClass();
        url = bds.getJdbcUrl();
        username = bds.getUser();
        password = bds.getPassword();
    }

    public static Spring getInstance() {
        return SpringHolder.INSTANCE;
    }

    private static class SpringHolder {

        private static final Spring INSTANCE = new Spring();
    }
}
