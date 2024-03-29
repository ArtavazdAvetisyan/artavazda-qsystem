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
package ru.apertum.qsystem.server.controller;

import org.springframework.transaction.TransactionStatus;
import ru.apertum.qsystem.common.SoundPlayer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.GregorianCalendar;
import org.dom4j.DocumentException;
import ru.apertum.qsystem.common.model.QCustomer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.dom4j.DocumentHelper;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import ru.apertum.qsystem.common.Uses;
import ru.apertum.qsystem.common.QLog;
import ru.apertum.qsystem.common.CustomerState;
import ru.apertum.qsystem.common.cmd.CmdParams;
import ru.apertum.qsystem.common.cmd.JsonRPC20;
import ru.apertum.qsystem.common.cmd.JsonRPC20Error;
import ru.apertum.qsystem.common.cmd.RpcGetAdvanceCustomer;
import ru.apertum.qsystem.common.cmd.RpcGetAllServices;
import ru.apertum.qsystem.common.cmd.RpcGetAuthorizCustomer;
import ru.apertum.qsystem.common.cmd.RpcGetBool;
import ru.apertum.qsystem.common.cmd.RpcGetGridOfWeek;
import ru.apertum.qsystem.common.cmd.RpcGetGridOfWeek.GridAndParams;
import ru.apertum.qsystem.common.cmd.RpcGetInfoTree;
import ru.apertum.qsystem.common.cmd.RpcGetInt;
import ru.apertum.qsystem.common.cmd.RpcGetPostponedPoolInfo;
import ru.apertum.qsystem.common.cmd.RpcGetRespList;
import ru.apertum.qsystem.common.cmd.RpcGetResultsList;
import ru.apertum.qsystem.common.cmd.RpcGetSelfSituation;
import ru.apertum.qsystem.common.cmd.RpcGetServerState;
import ru.apertum.qsystem.common.cmd.RpcGetSrt;
import ru.apertum.qsystem.common.cmd.RpcGetUsersList;
import ru.apertum.qsystem.common.cmd.RpcInviteCustomer;
import ru.apertum.qsystem.common.cmd.RpcStandInService;
import ru.apertum.qsystem.common.exceptions.ServerException;
import ru.apertum.qsystem.common.cmd.RpcBanList;
import ru.apertum.qsystem.server.MainBoard;
import ru.apertum.qsystem.server.QServer;
import ru.apertum.qsystem.server.ServerProps;
import ru.apertum.qsystem.server.Spring;
import ru.apertum.qsystem.server.model.QAdvanceCustomer;
import ru.apertum.qsystem.server.model.QAuthorizationCustomer;
import ru.apertum.qsystem.server.model.QPlanService;
import ru.apertum.qsystem.server.model.QService;
import ru.apertum.qsystem.server.model.QServiceTree;
import ru.apertum.qsystem.server.model.QUser;
import ru.apertum.qsystem.server.model.QUserList;
import ru.apertum.qsystem.server.model.calendar.CalendarTableModel;
import ru.apertum.qsystem.server.model.calendar.FreeDay;
import ru.apertum.qsystem.server.model.infosystem.QInfoTree;
import ru.apertum.qsystem.server.model.postponed.QPostponedList;
import ru.apertum.qsystem.server.model.response.QRespEvent;
import ru.apertum.qsystem.server.model.response.QResponseList;
import ru.apertum.qsystem.server.model.results.QResult;
import ru.apertum.qsystem.server.model.results.QResultList;
import ru.apertum.qsystem.server.model.schedule.QSchedule;

/**
 * Пул очередей.
 * Пул очередей - главная структура управления очередями.
 * В системе существуют несколько очередей, например для оказания разных услуг.
 * Пул получает XML-задания из сети, определяет требуемое действие.
 * Выполняет действия по организации пула.
 * Выполняет задания, касающиеся нескольких очередей.
 * Работает как singleton.
 * @author Evgeniy Egorov
 */
public final class Executer {

    public static Executer getInstance() {
        return ExecuterHolder.INSTANCE;
    }

    private static class ExecuterHolder {

        private static final Executer INSTANCE = new Executer();
    }

    /**
     * Конструктор пула очередей
     * Также нужно оперделить способ вывода информации для клиентов на табло.
     * @param property свойства и настройки по которым строим пул
     * @param ignoreWork создавать или нет статистику и табло.
     */
    private Executer() {
    }
    //
    //*******************************************************************************************************
    //**************************  ОБРАБОТЧИКИ ЗАДАНИЙ *******************************************************
    //*******************************************************************************************************
    //
    // задния, доступны по их именам
    private final HashMap<String, Task> tasks = new HashMap<>();

    /**
     * 
     * @author Evgeniy Egorov
     * Базовый класс обработчиков заданий.
     * сам себя складывает в HashMap<String, ATask> tasks.
     * метод process исполняет задание.
     */
    private class Task {

        protected final String name;
        protected CmdParams cmdParams;

        public Task(String name) {
            this.name = name;
            tasks.put(name, this);
        }

        public Object process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            QLog.l().logger().debug("Выполняем : \"" + name + "\"");
            this.cmdParams = cmdParams;
            return "";
        }
    }
    /**
     * Ключ блокировки для манипуляции с кстомерами
     */
    private final Lock clientTaskLock = new ReentrantLock();
    /**
     * Ставим кастомера в очередь.  
     */
    final AddCustomerTask addCustomerTask = new AddCustomerTask(Uses.TASK_STAND_IN);

    class AddCustomerTask extends Task {

        public AddCustomerTask(String name) {
            super(name);
        }

        @Override
        public RpcStandInService process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QService service = QServiceTree.getInstance().getById(cmdParams.serviceId);
            final QCustomer customer;
            // синхронизируем работу с клиентом
            clientTaskLock.lock();
            try {
                // Создадим вновь испеченного кастомера
                customer = new QCustomer(service.getNextNumber());
                // Определим кастомера в очередь
                customer.setService(service);
                // время постановки проставляется автоматом при создании кастомера.
                // Приоритет "как все"
                customer.setPriority(cmdParams.priority);
                // Введенные кастомером данные
                customer.setInput_data(cmdParams.textData);
                //добавим нового пользователя
                service.addCustomer(customer);
                // Состояние у него "Стою, жду".
                customer.setState(CustomerState.STATE_WAIT);
            } catch (Exception ex) {
                throw new ServerException("Ошибка при постановке клиента в очередь", ex);
            } finally {
                clientTaskLock.unlock();
            }
            QLog.l().logger().trace("С приоритетом " + customer.getPriority().get() + " К услуге \"" + cmdParams.serviceId + "\" -> " + service.getPrefix() + '\'' + service.getName() + '\'');
            // если кастомер добавился, то его обязательно отправить в ответ т.к.
            // он уже есть в системе
            try {
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что появился посетитель
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(service.getId().toString(), ServerProps.getInstance().getProps().getClientPort());
            } finally {
                return new RpcStandInService(customer);
            }
        }
    };
    /**
     * Пригласить кастомера, первого в очереди.
     */
    final Task inviteCustomerTask = new Task(Uses.TASK_INVITE_NEXT_CUSTOMER) {

        /**
         * Cинхронизируем, ато вызовут одного и того же.
         * А еще сдесь надо вызвать метод, который "проговорит" кого и куда вазвали.
         * Может случиться ситуация когда двое вызывают последнего кастомера, первому достанется, а второму нет.
         */
        @Override
        synchronized public RpcInviteCustomer process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // Определить из какой очереди надо выбрать кастомера.
            // Пока без учета коэфициента.
            // Для этого смотрим первых кастомеров во всех очередях и ищем первого среди первых.
            final QUser user = QUserList.getInstance().getById(cmdParams.userId); // юзер
            final boolean isRecall = user.getCustomer() != null;

            // есть ли у юзера вызванный кастомер? Тогда поторный вызов
            if (isRecall) {
                QLog.l().logger().debug("Повторный вызов кастомера №" + user.getCustomer().getPrefix() + user.getCustomer().getNumber() + " пользователем " + cmdParams.userId);

                // просигналим звуком
                SoundPlayer.inviteClient(user.getCustomer().getPrefix() + user.getCustomer().getNumber(), user.getPoint(), false);

                //разослать оповещение о том, что посетитель вызван повторно
                //рассылаем широковещетельно по UDP на определенный порт. Должно высветитьсяна основном табло
                MainBoard.getInstance().inviteCustomer(user, user.getCustomer());

                return new RpcInviteCustomer(user.getCustomer());
            }

            // бежим по очередям юзера и ищем первого из первых кастомера
            QCustomer customer = null;
            int servPriority = -1;// временная переменная для приоритета услуг
            // синхронизация работы с клиентом
            clientTaskLock.lock();
            try {
                for (QPlanService plan : user.getPlanServices()) {
                    final QService serv = QServiceTree.getInstance().getById(plan.getService().getId()); // очередная очередь

                    final QCustomer cust = serv.peekCustomer(); // первый в этой очереди
                    // если очередь пуста
                    if (cust == null) {
                        continue;
                    }
                    // учтем приоритетность кастомеров и приоритетность очередей для юзера в которые они стоят
                    final Integer prior = plan.getCoefficient();
                    if (prior > servPriority || (prior == servPriority && customer.compareTo(cust) == 1)) {
                        servPriority = prior;
                        customer = cust;
                    }
                }
                //Найденного самого первого из первых кастомера переносим на хранение юзеру, при этом удалив его из общей очереди.
                // Случай, когда всех разобрали, но вызов сделан
                //При приглашении очередного клиента пользователем очереди оказались пустые.
                if (customer == null) {
                    return new RpcInviteCustomer(null);
                }
                customer = QServiceTree.getInstance().getById(customer.getService().getId()).polCustomer();
            } catch (Exception ex) {
                throw new ServerException("Ошибка при постановке клиента в очередь" + ex);
            } finally {
                clientTaskLock.unlock();
            }
            if (customer == null) {
                throw new ServerException("Странная проблема с получением кастомера и удалением его из очереди.");
            }
            // определим юзеру кастомера, которого он вызвал.
            user.setCustomer(customer);
            // Поставил кастомеру юзера, который его вызвал.
            customer.setUser(user);
            // ставим время вызова
            customer.setCallTime(new Date());
            // кастомер переходит в состояние "приглашенности"
            customer.setState(customer.getState() == CustomerState.STATE_WAIT ? CustomerState.STATE_INVITED : CustomerState.STATE_INVITED_SECONDARY);

            // если кастомер вызвался, то его обязательно отправить в ответ
            // он уже есть у юзера
            try {
                // просигналим звуком
                SoundPlayer.inviteClient(user.getCustomer().getPrefix() + user.getCustomer().getNumber(), user.getPoint(), true);
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что появился вызванный посетитель
                // Должно высветитьсяна основном табло
                MainBoard.getInstance().inviteCustomer(user, user.getCustomer());
                //разослать оповещение о том, что посетителя вызвали, состояние очереди изменилось
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(customer.getService().getId().toString(), ServerProps.getInstance().getProps().getClientPort());
            } finally {
                return new RpcInviteCustomer(customer);
            }
        }
    };
    /**
     * Пригласить кастомера из пула отложенных
     */
    final Task invitePostponedTask = new Task(Uses.TASK_INVITE_POSTPONED) {

        /**
         * Cинхронизируем, ато вызовут одного и того же.
         * А еще сдесь надо вызвать метод, который "проговорит" кого и куда вазвали.
         * Может случиться ситуация когда двое вызывают последнего кастомера, первому достанется, а второму нет.
         */
        @Override
        synchronized public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // Определить из какой очереди надо выбрать кастомера.
            // Пока без учета коэфициента.
            // Для этого смотрим первых кастомеров во всех очередях и ищем первого среди первых.
            final QUser user = QUserList.getInstance().getById(cmdParams.userId); // юзер

            // выберем отложенного кастомера по ид
            final QCustomer customer = QPostponedList.getInstance().getById(cmdParams.customerId);
            if (customer == null) {
                return new JsonRPC20(new JsonRPC20Error(JsonRPC20Error.POSTPONED_NOT_FOUND, cmdParams.customerId));
            } else {
                QPostponedList.getInstance().removeElement(customer);
            }
            // определим юзеру кастомера, которого он вызвал.
            user.setCustomer(customer);
            // Поставил кастомеру юзера, который его вызвал.
            customer.setUser(user);
            // только что встал типо. Поросто время нахождения в отложенных не считаетка как ожидание очереди. Инвче в statistic ожидание огромное
            customer.setStandTime(new Date());
            // ставим время вызова
            customer.setCallTime(new Date());
            // ну и услугу определим
            customer.setService(QServiceTree.getInstance().getById(user.getPlanServices().get(0).getService().getId()));
            // кастомер переходит в состояние "приглашенности"
            customer.setState(CustomerState.STATE_INVITED_SECONDARY);
            // если кастомер вызвался, то его обязательно отправить в ответ
            // он уже есть у юзера
            try {
                // просигналим звуком
                //SoundPlayer.play("/ru/apertum/qsystem/server/sound/sound.wav");
                SoundPlayer.inviteClient(user.getCustomer().getPrefix() + user.getCustomer().getNumber(), user.getPoint(), true);
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что появился вызванный посетитель
                // Должно высветитьсяна основном табло
                MainBoard.getInstance().inviteCustomer(user, user.getCustomer());
                //разослать оповещение о том, что отложенного вызвали, состояние очереди изменилось не изменилось, но пул отложенных изменился
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(Uses.TASK_REFRESH_POSTPONED_POOL, ServerProps.getInstance().getProps().getClientPort());
            } finally {
                return new RpcInviteCustomer(customer);
            }
        }
    };
    /**
     * Получить перечень услуг
     */
    final Task getServicesTask = new Task(Uses.TASK_GET_SERVICES) {

        @Override
        public RpcGetAllServices process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetAllServices(new RpcGetAllServices.ServicesForWelcome(QServiceTree.getInstance().getRoot(), ServerProps.getInstance().getProps().getStartTime(), ServerProps.getInstance().getProps().getFinishTime()));
        }
    };
    /**
     * Если услуга требует ввода данных пользователем, то нужно получить эти данные из диалога ввода
     * если ввели, то тут спрашиваем у сервера есть ли возможность встать в очередь с такими введенными данными
     * @return 1 - превышен, 0 - можно встать. 2 - забанен
     */
    final Task aboutServicePersonLimit = new Task(Uses.TASK_ABOUT_SERVICE_PERSON_LIMIT) {

        @Override
        public RpcGetInt process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            if (RpcBanList.getInstance().isBaned(cmdParams.textData)) {
                return new RpcGetInt(2);
            }
            // Если лимит количества подобных введенных данных кастомерами в день достигнут
            final QService srv = QServiceTree.getInstance().getById(cmdParams.serviceId);
            return new RpcGetInt(srv.isLimitPersonPerDayOver(cmdParams.textData) ? 1 : 0);
        }
    };
    /**
     * Получить описание состояния услуги
     */
    final Task aboutTask = new Task(Uses.TASK_ABOUT_SERVICE) {

        @Override
        public RpcGetInt process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // Проверим оказывается ли сейчас эта услуга
            int min = Uses.LOCK_INT;
            final Date day = new Date();
            final QService srv = QServiceTree.getInstance().getById(cmdParams.serviceId);
            // Если не лимит количества возможных обработанных в день достигнут
            if (srv.isLimitPerDayOver()) {
                QLog.l().logger().warn("Услуга \"" + cmdParams.serviceId + "\" не обрабатывается исходя из достижения лимита возможной обработки кастомеров в день.");
                return new RpcGetInt(Uses.LOCK_PER_DAY_INT);
            }
            // Если нет расписания, календаря или выходной то отказ по расписанию
            if (srv.getSchedule() == null || checkFreeDay(day, new Long(1)) || (srv.getCalendar() != null && checkFreeDay(day, srv.getCalendar().getId()))) {
                min = Uses.LOCK_FREE_INT;
            } else {
                // Определим время начала и нонца работы на этот день
                final QSchedule sch = srv.getSchedule();
                final GregorianCalendar gc_day = new GregorianCalendar();
                gc_day.setTime(day);
                Date start = null;
                Date end = null;
                if (sch.getType() == 1) {
                    if (0 == (gc_day.get(GregorianCalendar.DAY_OF_MONTH) % 2)) {
                        start = sch.getTime_begin_1();
                        end = sch.getTime_end_1();
                    } else {
                        start = sch.getTime_begin_2();
                        end = sch.getTime_end_2();
                    }
                } else {
                    switch (gc_day.get(GregorianCalendar.DAY_OF_WEEK)) {
                        case 2:
                            start = sch.getTime_begin_1();
                            end = sch.getTime_end_1();
                            break;
                        case 3:
                            start = sch.getTime_begin_2();
                            end = sch.getTime_end_2();
                            break;
                        case 4:
                            start = sch.getTime_begin_3();
                            end = sch.getTime_end_3();
                            break;
                        case 5:
                            start = sch.getTime_begin_4();
                            end = sch.getTime_end_4();
                            break;
                        case 6:
                            start = sch.getTime_begin_5();
                            end = sch.getTime_end_5();
                            break;
                        case 7:
                            start = sch.getTime_begin_6();
                            end = sch.getTime_end_6();
                            break;
                        case 1:
                            start = sch.getTime_begin_7();
                            end = sch.getTime_end_7();
                            break;
                        default:
                            ;
                    }
                }// Определили начало и конец рабочего дня на сегодня
                // Если работаем в этот день то определим попадает ли "сейчас" в рабочий промежуток
                if (!(start == null || end == null)) {
                    final int h = gc_day.get(GregorianCalendar.HOUR_OF_DAY);
                    final int m = gc_day.get(GregorianCalendar.MINUTE);
                    gc_day.setTime(start);
                    final int sh = gc_day.get(GregorianCalendar.HOUR_OF_DAY);
                    final int sm = gc_day.get(GregorianCalendar.MINUTE);
                    gc_day.setTime(end);
                    final int eh = gc_day.get(GregorianCalendar.HOUR_OF_DAY);
                    final int em = gc_day.get(GregorianCalendar.MINUTE);
                    if (!(sh * 60 + sm <= h * 60 + m && h * 60 + m <= eh * 60 + em) && (!((sh == eh) && (sm == em)))) {
                        min = Uses.LOCK_FREE_INT;
                    }
                } else {
                    min = Uses.LOCK_FREE_INT;
                }
            }
            // Если не работаем, то отправим ответ и прекратим выполнение
            if (min == Uses.LOCK_FREE_INT) {
                QLog.l().logger().warn("Услуга \"" + cmdParams.serviceId + "\" не обрабатывается исходя из рабочего расписания.");
                return new RpcGetInt(min);
            }
            // бежим по юзерам и смотрим обрабатывают ли они услугу
            // если да, то возьмем все услуги юзера и  сложим всех кастомеров в очередях
            // самую маленькую сумму отправим в ответ по запросу.
            for (QUser user : QUserList.getInstance().getItems()) {
                if (user.hasService(cmdParams.serviceId)) {
                    // теперь по услугам юзера
                    int sum = 0;
                    for (QPlanService planServ : user.getPlanServices()) {
                        final QService service = QServiceTree.getInstance().getById(planServ.getService().getId());
                        sum = sum + service.getCountCustomers();
                    }
                    if (min > sum) {
                        min = sum;
                    }
                }
            }
            if (min == Uses.LOCK_INT) {
                QLog.l().logger().warn("Услуга \"" + cmdParams.serviceId + "\" не обрабатывается ни одним пользователем.");
            }
            return new RpcGetInt(min);
        }
    };
    /**
     * Получить описание пользователей для выбора
     */
    final Task getUsersTask = new Task(Uses.TASK_GET_USERS) {

        @Override
        public RpcGetUsersList process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            checkUserLive.refreshUsersFon();
            return new RpcGetUsersList(QUserList.getInstance().getItems());
        }
    };
    /**
     * Получить состояние сервера.
     */
    private final Task getServerState = new Task(Uses.TASK_SERVER_STATE) {

        @Override
        public RpcGetServerState process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final LinkedList<RpcGetServerState.ServiceInfo> srvs = new LinkedList<>();

            for (QService service : QServiceTree.getInstance().getNodes()) {
                if (service.isLeaf()) {
                    final QCustomer customer = service.peekCustomer();
                    srvs.add(new RpcGetServerState.ServiceInfo(service, service.getCountCustomers(), customer != null ? customer.getPrefix() + customer.getNumber() : "Ожидающих нет"));
                }
            }
            return new RpcGetServerState(srvs);
        }
    };
    /**
     * Получить подтверждение о живучести.
     */
    private final LiveTask checkUserLive = new LiveTask(Uses.TASK_I_AM_LIVE);
    private static final Object forRefr = new Object();

    private class LiveTask extends Task {

        public LiveTask(String name) {
            super(name);
        }
        /**
         * ID пользователя -> его адрес
         */
        private final HashMap<Long, String> addrByID = new HashMap<>();
        /**
         * Адрес пользователя -> его ID
         */
        private final HashMap<String, Long> idByAddr = new HashMap<>();
        /**
         * Адрес пользователя -> его байтовое прeдставление
         */
        private final HashMap<String, byte[]> ipByAddr = new HashMap<>();

        public boolean hasId(Long id) {
            synchronized (forRefr) {
                return addrByID.get(id) != null;
            }
        }

        /**
         * Опросим всю сетку на предмет пользователей параллельно происходящему.
         */
        public void refreshUsersFon() {
            Thread th = new Thread(new Runnable() {

                @Override
                public void run() {
                    refreshUsers();
                }
            });
            th.start();
        }

        /**
         * Опросим всю сетку на предмет пользователей.
         */
        public void refreshUsers() {
            synchronized (forRefr) {
                // подотрем все списки
                final int i = ipByAddr.size();
                ipByAddr.clear();
                idByAddr.clear();
                addrByID.clear();
                // полная рассылка
                Uses.sendUDPBroadcast(Uses.HOW_DO_YOU_DO, ServerProps.getInstance().getProps().getClientPort());
                try {
                    int k = 0;
                    while (ipByAddr.size() < i && k < 8) {
                        k++;
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ex) {
                    throw new ServerException("Таймер. " + ex.toString());
                }
            }
        }

        /**
         * Проверка залогиневшегося чела по имени
         * @param userId id чела для проверки
         * @return есть юзер с таким именем или нет
         */
        public boolean checkUserName(Long userId) {
            synchronized (forRefr) {
                if (addrByID.get(userId) != null) {
                    final byte[] ip = ipByAddr.get(addrByID.get(userId));
                    QLog.l().logger().debug("Отправить запрос на подтверждение активности на \"" + addrByID.get(userId) + "\" пользователя \"" + userId + "\".");
                    // подотрем перед проверкой
                    idByAddr.remove(addrByID.get(userId));
                    ipByAddr.remove(addrByID.get(userId));
                    addrByID.remove(userId);
                    // проверим
                    try {
                        Uses.sendUDPMessage(Uses.HOW_DO_YOU_DO, InetAddress.getByAddress(ip), ServerProps.getInstance().getProps().getClientPort());
                    } catch (UnknownHostException ex) {
                        throw new ServerException("Че адрес не существует??? " + new String(ip) + " " + ex);
                    }
                    // подождем ответа
                    int i = 0;
                    while (addrByID.get(userId) == null && i < 70) {
                        i++;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            throw new ServerException("Таймер. " + ex.toString());
                        }
                    }
                    return addrByID.get(userId) != null;
                } else {
                    return false;
                }
            }
        }

        /**
         * Проверка залогиневшегося чела по адресу
         * @param ipAdress адрес для проверки
         * @return есть там юзер или нет
         */
        public boolean checkUserAddress(String ipAdress) throws UnknownHostException {
            synchronized (forRefr) {
                if (idByAddr.get(ipAdress) != null) {
                    final byte[] ip = ipByAddr.get(ipAdress);
                    QLog.l().logger().debug("Отправить запрос на подтверждение активности на \"" + ipAdress + "\" пользователя \"" + idByAddr.get(ipAdress) + "\".");
                    // подотрем перед проверкой
                    addrByID.remove(idByAddr.get(ipAdress));
                    idByAddr.remove(ipAdress);
                    ipByAddr.remove(ipAdress);
                    // проверим
                    try {
                        Uses.sendUDPMessage(Uses.HOW_DO_YOU_DO, InetAddress.getByAddress(ip), ServerProps.getInstance().getProps().getClientPort());
                    } catch (UnknownHostException ex) {
                        throw new ServerException("Че адрес не существует??? " + ipAdress + " " + ex);
                    }
                    // подождем ответа
                    int i = 0;
                    while (idByAddr.get(ipAdress) == null && i < 70) {
                        i++;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            throw new ServerException("Таймер. " + ex.toString());
                        }
                    }
                    return idByAddr.get(ipAdress) != null;
                } else {
                    return false;
                }
            }
        }

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            synchronized (forRefr) {
                super.process(cmdParams, ipAdress, IP);
                addrByID.put(cmdParams.userId, ipAdress);
                idByAddr.put(ipAdress, cmdParams.userId);
                ipByAddr.put(ipAdress, IP);
            }
            return new JsonRPC20();
        }
    };
    /**
     * Получить описание состояния очередей для пользователя.
     */
    final Task getSelfServicesTask = new Task(Uses.TASK_GET_SELF_SERVICES) {

        @Override
        public RpcGetSelfSituation process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            final LinkedList<RpcGetSelfSituation.SelfService> servs = new LinkedList<>();
            for (QPlanService planService : user.getPlanServices()) {
                final QService service = QServiceTree.getInstance().getById(planService.getService().getId());
                servs.add(new RpcGetSelfSituation.SelfService(service, service.getCountCustomers(), planService.getCoefficient(), planService.getFlexible_coef()));
            }
            // нужно сделать вставочку приглашенного юзера, если он есть
            return new RpcGetSelfSituation(new RpcGetSelfSituation.SelfSituation(servs, user.getCustomer(), QPostponedList.getInstance().getPostponedCustomers()));
        }
    };
    /**
     * Получить описание состояния очередей для пользователя и проверить
     * Отсечем дубляжи запуска от одних и тех же юзеров. но с разных компов
     */
    final Task getCheckSelfTask = new Task(Uses.TASK_GET_SELF_SERVICES_CHECK) {

        @Override
        public synchronized RpcGetBool process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // Отсечем дубляжи запуска от одних и тех же юзеров. но с разных компов
            // пришло с запросом от юзера имеющегося в региных
            //System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@" + userId);
            if (checkUserLive.hasId(cmdParams.userId)) {
                QLog.l().logger().debug(cmdParams.userId + " ACCESS_DENY");
                return new RpcGetBool(false);
            }
            // чтоб вперед не влез если одновременно два новых
            checkUserLive.process(cmdParams, ipAdress, IP);
            return new RpcGetBool(true);
        }
    };
    /**
     * Получить состояние пула отложенных
     */
    final Task getPostponedPoolInfo = new Task(Uses.TASK_GET_POSTPONED_POOL) {

        @Override
        public synchronized RpcGetPostponedPoolInfo process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetPostponedPoolInfo(QPostponedList.getInstance().getPostponedCustomers());
        }
    };
    /**
     * Получить список забаненных
     */
    final Task getBanList = new Task(Uses.TASK_GET_BAN_LIST) {

        @Override
        public RpcBanList process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            RpcBanList.getInstance().udo(null);
            return RpcBanList.getInstance();
        }
    };
    /**
     * Удалить вызванного юзером кастомера по неявке.
     */
    final Task killCustomerTask = new Task(Uses.TASK_KILL_NEXT_CUSTOMER) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            // Если кастомер имел что-то введенное на пункте регистрации, то удалить всех таких кастомеров с такими введеными данными
            // и отправить его в бан, ибо нехрен набирать кучу талонов и просирать очереди.
            if (user.getCustomer().getInput_data() != null && !"".equals(user.getCustomer().getInput_data())) {
                int cnt = 0;
                for (QService service : QServiceTree.getInstance().getNodes()) {
                    final LinkedList<QCustomer> for_del = new LinkedList<>();
                    for (QCustomer customer : service.getClients()) {
                        if (user.getCustomer().getInput_data().equals(customer.getInput_data())) {
                            for_del.add(customer);
                        }
                    }
                    for (QCustomer qCustomer : for_del) {
                        service.removeCustomer(qCustomer);
                    }
                    cnt = cnt + for_del.size();
                }
                if (cnt != 0) {
                    RpcBanList.getInstance().addToBanList(user.getCustomer().getInput_data());
                }
                QLog.l().logger().debug("Вместе с кастомером " + user.getCustomer().getPrefix() + "-" + user.getCustomer().getNumber() + " он ввел \"" + user.getCustomer().getInput_data() + "\" удалили еще его " + cnt + " проявлений.");
            }

            // кастомер переходит в состояние "умерщвленности"
            user.getCustomer().setState(CustomerState.STATE_DEAD);
            try {
                user.setCustomer(null);//бобик сдох и медальки не осталось
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что посетитель откланен
                // Должно подтереться основном табло
                MainBoard.getInstance().killCustomer(user);
            } finally {
                return new JsonRPC20();
            }
        }
    };
    /**
     * Начать работу с вызванноым кастомером.
     */
    final Task getStartCustomerTask = new Task(Uses.TASK_START_CUSTOMER) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            // Время старта работы с юзера с кастомером.
            user.getCustomer().setStartTime(new Date());
            // кастомер переходит в состояние "Начала обработки" или "Продолжение работы"
            user.getCustomer().setState(user.getCustomer().getState() == CustomerState.STATE_INVITED ? CustomerState.STATE_WORK : CustomerState.STATE_WORK_SECONDARY);
            MainBoard.getInstance().workCustomer(user);
            // сохраняем состояния очередей.
            QServer.savePool();
            return new JsonRPC20();
        }
    };
    /**
     * Перемещение вызванного юзером кастомера в пул отложенных.
     */
    final Task customerToPostponeTask = new Task(Uses.TASK_CUSTOMER_TO_POSTPON) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // вот он все это творит
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            // вот над этим пациентом
            final QCustomer customer = user.getCustomer();
            // статус
            customer.setPostponedStatus(cmdParams.textData);
            // в этом случае завершаем с пациентом
            //"все что хирург забыл в вас - в пул отложенных"
            // но сначала обозначим результат работы юзера с кастомером, если такой результат найдется в списке результатов
            customer.setFinishTime(new Date());
            // кастомер переходит в состояние "Завершенности", но не "мертвости"
            customer.setState(CustomerState.STATE_POSTPONED);
            try {
                user.setCustomer(null);//бобик сдох но медалька осталось, отправляем в пулл
                customer.setUser(null);
                QPostponedList.getInstance().addElement(customer);
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что посетитель отложен
                Uses.sendUDPBroadcast(Uses.TASK_REFRESH_POSTPONED_POOL, ServerProps.getInstance().getProps().getClientPort());
                //рассылаем широковещетельно по UDP на определенный порт. Должно высветитьсяна основном табло
                MainBoard.getInstance().killCustomer(user);
            } catch (Throwable t) {
                QLog.l().logger().error("Загнулось под конец.", t);
            } finally {
                return new JsonRPC20();
            }
        }
    };
    /**
     * Изменение отложенному кастомеру статуса
     */
    final Task postponCustomerChangeStatusTask = new Task(Uses.TASK_POSTPON_CHANGE_STATUS) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QCustomer cust = QPostponedList.getInstance().getById(cmdParams.customerId);
            if (cust != null) {
                cust.setPostponedStatus(cmdParams.textData);
                //разослать оповещение о том, что посетителя вызвали, состояние очереди изменилось
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(Uses.TASK_REFRESH_POSTPONED_POOL, ServerProps.getInstance().getProps().getClientPort());
                return new JsonRPC20();
            } else {
                return new JsonRPC20(new JsonRPC20Error(JsonRPC20Error.POSTPONED_NOT_FOUND, cmdParams.customerId));
            }

        }
    };
    /**
     * Закончить работу с вызванноым кастомером.
     */
    final Task getFinishCustomerTask = new Task(Uses.TASK_FINISH_CUSTOMER) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // вот он все это творит
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            // вот над этим пациентом
            final QCustomer customer = user.getCustomer();
            // комменты
            customer.setTempComments(cmdParams.textData);
            // надо посмотреть не требует ли этот кастомер возврата в какую либо очередь.
            final QService backSrv = user.getCustomer().getServiceForBack();
            if (backSrv != null) {
                QLog.l().logger().debug("Требуется возврат после редиректа.");
                // действия по завершению работы юзера над кастомером
                customer.setFinishTime(new Date());
                // кастомер переходит в состояние "возврата", тут еще и в базу скинется, если надо.
                customer.setState(CustomerState.STATE_BACK, backSrv.getId());
                // переставить кастомера в очередь к пункту возврата
                backSrv.addCustomer(customer);
                // надо кастомера инициализить др. услугой

                // Поставил кастомеру юзера, который его вызвал.
                // юзер в другой очереди наверное другой
                customer.setUser(null);
                // теперь стоит к новой услуги.
                customer.setService(backSrv);

                //разослать оповещение о том, что появился посетитель после редиректа
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(backSrv.getId().toString(), ServerProps.getInstance().getProps().getClientPort());
                QLog.l().logger().info("Клиент \"" + user.getCustomer().getPrefix() + user.getCustomer().getNumber() + "\" возвращен к услуге \"" + backSrv.getName() + "\"");
            } else {
                QLog.l().logger().debug("В морг пациента.");

                // в этом случае завершаем с пациентом
                //"все что хирург забыл в вас - ваше"
                // но сначала обозначим результат работы юзера с кастомером, если такой результат найдется в списке результатов
                // может приехать -1 если результат не требовался
                final QResult result;
                if (cmdParams.resultId != -1) {
                    result = QResultList.getInstance().getById(cmdParams.resultId);
                } else {
                    result = null;
                }
                ((QCustomer) customer).setResult(result);
                customer.setFinishTime(new Date());
                // кастомер переходит в состояние "Завершенности", но не "мертвости"
                customer.setState(CustomerState.STATE_FINISH);
            }
            try {
                user.setCustomer(null);//бобик сдох и медальки не осталось
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что посетитель откланен
                //рассылаем широковещетельно по UDP на определенный порт. Должно высветитьсяна основном табло
                MainBoard.getInstance().killCustomer(user);
            } finally {
                return new JsonRPC20();
            }
        }
    };
    /**
     * Переадресовать клиента к другой услуге.
     */
    final Task redirectCustomerTask = new Task(Uses.TASK_REDIRECT_CUSTOMER) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            final QCustomer customer = user.getCustomer();
            // комменты по редиректу
            customer.setTempComments(cmdParams.textData);
            // Переставка в другую очередь
            // Название старой очереди
            final QService oldService = customer.getService();
            // вот она новая очередь.
            final QService newService = QServiceTree.getInstance().getById(cmdParams.serviceId);
            // действия по завершению работы юзера над кастомером
            customer.setFinishTime(new Date());
            // кастомер переходит в состояние "перенаправленности", тут еще и в базу скинется, если надо.
            customer.setState(CustomerState.STATE_REDIRECT, cmdParams.serviceId);
            // надо кастомера инициализить др. услугой
            // юзер в другой очереди наверное другой
            customer.setUser(null);
            // теперь стоит к новой услуги.
            customer.setService(newService);
            // если редиректят в прежнюю услугу, то это по факту не ридирект(иначе карусель)
            // по этому в таком случае кастомера отправляют в конец очереди к этой же услуге.
            // для этого просто не учитываем смену приоритета и галку возврата. 
            if (!oldService.getId().equals(cmdParams.serviceId)) {
                // т.к. переставленный, то надо поменять ему приоритет.
                customer.setPriority(Uses.PRIORITY_HI);
                // при редиректе надо убрать у кастомера признак старого юзера, время начала обработки.
                //это произойдет далее при вызове setCustomer(null).
                // и добавить, если надо, пункт возврата.
                // теперь пункт возврата
                if (cmdParams.requestBack) { // требует ли возврата в прежнюю очередь
                    customer.addServiceForBack(oldService);
                }
            } else {
                // только что встал типо
                customer.setStandTime(new Date());
            }
            //С НАЧАЛА ПОДОТРЕМ ПОТОМ ПЕРЕСТАВИМ!!!
            //с новым приоритетом ставим в новую очередь, приоритет должет
            //позволить вызваться ему сразу за обрабатываемыми кастомерами
            newService.addCustomer(customer);
            user.setCustomer(null);//бобик сдох и медальки не осталось, воскрес вместе со старой медалькой в соседней очереди

            try {
                // сохраняем состояния очередей.
                QServer.savePool();
                //разослать оповещение о том, что появился посетитель
                //рассылаем широковещетельно по UDP на определенный порт
                Uses.sendUDPBroadcast(newService.getId().toString(), ServerProps.getInstance().getProps().getClientPort());
                Uses.sendUDPBroadcast(oldService.getId().toString(), ServerProps.getInstance().getProps().getClientPort());
                //разослать оповещение о том, что посетитель откланен
                //рассылаем широковещетельно по UDP на определенный порт. Должно подтереться на основном табло
                MainBoard.getInstance().killCustomer(user);
            } finally {
                return new JsonRPC20();
            }
        }
    };
    /**
     * Привязка услуги пользователю на горячую по команде. Это обработчик этой команды.
     */
    final Task setServiceFire = new Task(Uses.TASK_SET_SERVICE_FIRE) {

        @Override
        synchronized public RpcGetSrt process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            if (cmdParams.userId == null || cmdParams.serviceId == null) {
                return new RpcGetSrt("Неверные попараметры запроса.");
            }
            if (!QServiceTree.getInstance().hasById(cmdParams.serviceId)) {
                return new RpcGetSrt("Требуемая услуга не присутствует в текущей загруженной конфигурации сервера.");
            }
            final QService service = QServiceTree.getInstance().getById(cmdParams.serviceId);
            if (!QUserList.getInstance().hasById(cmdParams.userId)) {
                return new RpcGetSrt("Требуемый пользователь не присутствует в текущей загруженной конфигурации сервера.");
            }
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);

            if (user.hasService(cmdParams.serviceId)) {
                return new RpcGetSrt("Требуемая услуга уже назначена этому пользователю.");
            }
            user.addPlanService(service, cmdParams.coeff);
            //разослать оповещение о том, что у пользователя поменялась конфигурация услуг
            //рассылаем широковещетельно по UDP на определенный порт
            Uses.sendUDPBroadcast(String.valueOf(cmdParams.userId), ServerProps.getInstance().getProps().getClientPort());
            return new RpcGetSrt("Услуга \"" + cmdParams.serviceId + "\" назначена пользователю \"" + cmdParams.userId + "\" успешно.");
        }
    };
    /**
     * Удаление привязка услуги пользователю на горячую по команде. Это обработчик этой команды.
     */
    final Task deleteServiceFire = new Task(Uses.TASK_DELETE_SERVICE_FIRE) {

        @Override
        synchronized public RpcGetSrt process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            if (cmdParams.userId == null || cmdParams.serviceId == null) {
                return new RpcGetSrt("Неверные попараметры запроса.");
            }
            if (!QServiceTree.getInstance().hasById(cmdParams.serviceId)) {
                return new RpcGetSrt("Требуемая услуга не присутствует в текущей загруженной конфигурации сервера.");
            }
            if (!QUserList.getInstance().hasById(cmdParams.userId)) {
                return new RpcGetSrt("Требуемый пользователь не присутствует в текущей загруженной конфигурации сервера.");
            }
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);

            if (!user.hasService(cmdParams.serviceId)) {
                return new RpcGetSrt("Требуемая услуга не назначена этому пользователю.");
            }
            user.deletePlanService(cmdParams.serviceId);
            //разослать оповещение о том, что у пользователя поменялась конфигурация услуг
            //рассылаем широковещетельно по UDP на определенный порт
            Uses.sendUDPBroadcast(String.valueOf(cmdParams.userId), ServerProps.getInstance().getProps().getClientPort());
            return new RpcGetSrt("Услуга \"" + cmdParams.serviceId + "\" удалена у пользователя \"" + cmdParams.userId + "\" успешно.");
        }
    };
    /**
     * Получение конфигурации главного табло - ЖК или плазмы.
     * Это XML-файл лежащий в папку приложения mainboard.xml
     */
    final Task getBoardConfig = new Task(Uses.TASK_GET_BOARD_CONFIG) {

        @Override
        public RpcGetSrt process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetSrt(MainBoard.getInstance().getConfig().asXML());
        }
    };
    /**
     * Сохранение конфигурации главного табло - ЖК или плазмы.
     * Это XML-файл лежащий в папку приложения mainboard.xml
     */
    final Task saveBoardConfig = new Task(Uses.TASK_SAVE_BOARD_CONFIG) {

        @Override
        synchronized public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            try {
                MainBoard.getInstance().saveConfig(DocumentHelper.parseText(cmdParams.textData).getRootElement());
            } catch (DocumentException ex) {
                QLog.l().logger().error("Не сохранилась конфигурация табло.", ex);
            }
            return new JsonRPC20();
        }
    };
    /**
     * Получение таблици записанных ранее клиентов на неделю.
     */
    final Task getGridOfWeek = new Task(Uses.TASK_GET_GRID_OF_WEEK) {

        @Override
        public RpcGetGridOfWeek process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            //Определим услугу
            final QService service = QServiceTree.getInstance().getById(cmdParams.serviceId);
            final QSchedule sch = service.getSchedule();
            if (sch == null) {
                return new RpcGetGridOfWeek(new RpcGetGridOfWeek.GridAndParams("Требуемая услуга не имеет расписания."));
            }

            final Date startWeek = new Date(cmdParams.date);
            final GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(startWeek);
            gc.set(GregorianCalendar.DAY_OF_YEAR, gc.get(GregorianCalendar.DAY_OF_YEAR) + 7);
            final Date endWeek = gc.getTime();

            QLog.l().logger().trace("Загрузим уже занятых позиций ранее записанными кастомерами от " + Uses.format_for_rep.format(startWeek) + " до " + Uses.format_for_rep.format(endWeek));
            // Загрузим уже занятых позиций ранее записанными кастомерами
            List<QAdvanceCustomer> advCustomers = Spring.getInstance().getHt().find("FROM QAdvanceCustomer a WHERE advance_time >'" + Uses.format_for_rep.format(startWeek) + "' and advance_time <= '" + Uses.format_for_rep.format(endWeek) + "' and service_id = " + service.getId());

            final GridAndParams advCusts = new GridAndParams();
            advCusts.setStartTime(ServerProps.getInstance().getProps().getStartTime());
            advCusts.setFinishTime(ServerProps.getInstance().getProps().getFinishTime());
            advCusts.setAdvanceLimit(service.getAdvanceLimit());
            advCusts.setAdvanceLimitPeriod(service.getAdvanceLimitPeriod() == null ? 0 : service.getAdvanceLimitPeriod());
            // сформируем список доступных времен
            Date day = startWeek;
            while (day.before(endWeek)) {
                final GregorianCalendar gc_day = new GregorianCalendar();
                gc_day.setTime(day);
                // Определим по календарю рабочий ли день.
                // Календаря может быть два, общий с id=1 и персонально настроенный
                // Если день определяется как выходной(присутствует в БД в таблице выходных дней), то переходим к следующему дню
                if (!checkFreeDay(day, new Long(1)) && !(service.getCalendar() != null && checkFreeDay(day, service.getCalendar().getId()))) {
                    // Определим время начала и нонца работы на этот день
                    Date start = null;
                    Date end = null;
                    if (sch.getType() == 1) {
                        if (0 == (gc_day.get(GregorianCalendar.DAY_OF_MONTH) % 2)) {
                            start = sch.getTime_begin_1();
                            end = sch.getTime_end_1();
                        } else {
                            start = sch.getTime_begin_2();
                            end = sch.getTime_end_2();
                        }
                    } else {
                        switch (gc_day.get(GregorianCalendar.DAY_OF_WEEK)) {
                            case 2:
                                start = sch.getTime_begin_1();
                                end = sch.getTime_end_1();
                                break;
                            case 3:
                                start = sch.getTime_begin_2();
                                end = sch.getTime_end_2();
                                break;
                            case 4:
                                start = sch.getTime_begin_3();
                                end = sch.getTime_end_3();
                                break;
                            case 5:
                                start = sch.getTime_begin_4();
                                end = sch.getTime_end_4();
                                break;
                            case 6:
                                start = sch.getTime_begin_5();
                                end = sch.getTime_end_5();
                                break;
                            case 7:
                                start = sch.getTime_begin_6();
                                end = sch.getTime_end_6();
                                break;
                            case 1:
                                start = sch.getTime_begin_7();
                                end = sch.getTime_end_7();
                                break;
                            default:
                                ;
                        }

                    }
                    // Если работаем в этот день то определим часы на которые еще можно записаться
                    if (!(start == null || end == null)) {

                        // бежим по часам внутри дня
                        while (start.before(end)) {
                            int cnt = 0;
                            // пробигаем по кастомерам записанным
                            for (QAdvanceCustomer advCustomer : advCustomers) {
                                gc.setTime(start);
                                final int s = gc.get(GregorianCalendar.HOUR_OF_DAY);
                                gc.setTime(advCustomer.getAdvanceTime());
                                final int e = gc.get(GregorianCalendar.HOUR_OF_DAY);
                                // Если совпел день и час, то увеличим счетчик записавшихся на этот час
                                if (gc.get(GregorianCalendar.DAY_OF_YEAR) == gc_day.get(GregorianCalendar.DAY_OF_YEAR) && s == e) {
                                    cnt++;
                                    // Защита от того чтобы один и тодже клиент не записался предварительно в одну услугу на одну дату.
                                    // данный предв.кастомер не должен быть таким же как и авторизовавшийся на этот час
                                    if (cmdParams.customerId != -1
                                            && advCustomer.getAuthorizationCustomer() != null
                                            && advCustomer.getAuthorizationCustomer().getId() != null
                                            && advCustomer.getAuthorizationCustomer().getId().equals(cmdParams.customerId)) {
                                        cnt = 1999999999;
                                        break;
                                    }
                                }
                            }
                            // если еще количество записавшихся не привысило ограничение по услуге, то добавил этот час как доступный для записи
                            if (cnt < service.getAdvanceLimit()) {
                                gc.setTime(day);
                                final GregorianCalendar gc2 = new GregorianCalendar();
                                gc2.setTime(start);
                                gc.set(GregorianCalendar.HOUR_OF_DAY, gc2.get(GregorianCalendar.HOUR_OF_DAY));
                                gc.set(GregorianCalendar.MINUTE, 0);
                                advCusts.addTime(gc.getTime());
                            }
                            // перейдем на следующий час
                            gc.setTime(start);
                            gc.set(GregorianCalendar.HOUR_OF_DAY, gc.get(GregorianCalendar.HOUR_OF_DAY) + 1);
                            start = gc.getTime();
                        }


                    }
                } // проверка на нерабочий день календаря
                // переход на следующий день
                gc_day.set(GregorianCalendar.DAY_OF_YEAR, gc_day.get(GregorianCalendar.DAY_OF_YEAR) + 1);
                day = gc_day.getTime();
            }
            return new RpcGetGridOfWeek(advCusts);
        }
    };

    /**
     * Проверка даты на нерабочую в определенном календаре
     * @param date проверяемая дата, важен месяц и день
     * @param calcId в каком календаре будем проверять
     * @return Выходной день в этом календаре или нет
     */
    private static boolean checkFreeDay(Date date, Long calcId) {
        final GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(date);
        final int m = gc.get(GregorianCalendar.MONTH);
        final int d = gc.get(GregorianCalendar.DAY_OF_MONTH);
        for (FreeDay day : CalendarTableModel.getFreeDays(calcId)) {
            gc.setTime(day.getDate());
            if (m == gc.get(GregorianCalendar.MONTH) && d == gc.get(GregorianCalendar.DAY_OF_MONTH)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Записать кастомера предварительно в услугу.
     */
    final Task standAdvanceInService = new Task(Uses.TASK_ADVANCE_STAND_IN) {

        @Override
        synchronized public RpcGetAdvanceCustomer process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);

            final QService service = QServiceTree.getInstance().getById(cmdParams.serviceId);
            QLog.l().logger().trace("Предварительно записываем к услуге \"" + cmdParams.serviceId + "\" -> " + service.getPrefix() + ' ' + service.getName() + '\'');
            // Создадим вновь испеченного кастомера
            final QAdvanceCustomer customer = new QAdvanceCustomer();

            // Определим ID авторизованного пользователя, если небыло авторизации, то оно = -1
            final Long authCustonerID = cmdParams.customerId;
            // выкачаем из базы зарегинова
            QAuthorizationCustomer acust = new QAuthorizationCustomer();
            if (cmdParams.customerId != -1) {
                Spring.getInstance().getHt().load(acust, authCustonerID);
                if (acust.getId() == null || acust.getName() == null) {
                    throw new ServerException("Авторизация не успешна.");
                }
            } else {
                acust = null;
            }
            customer.setAuthorizationCustomer(acust);
            // Определим дату и время для кастомера
            final Date startTime = new Date(cmdParams.date);
            //хорошо бы отсекать повторную запись к этому же специалиста на этот же день
            customer.setAdvanceTime(startTime);
            customer.setService(service);
            // время постановки проставляется автоматом при создании кастомера.
            // Приоритет "как все"
            customer.setPriority(2);

            //сохраним нового предварительного пользователя
            QLog.l().logger().debug("Старт сохранения предварительной записи в СУБД.");
            //Uses.getSessionFactory().merge(this);
            Spring.getInstance().getTt().execute(new TransactionCallbackWithoutResult() {

                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    try {
                        Spring.getInstance().getHt().saveOrUpdate(customer);
                        QLog.l().logger().debug("Сохранили.");
                    } catch (Exception ex) {
                        QLog.l().logger().error("Ошибка при сохранении \n" + ex.toString() + "\n" + ex.getStackTrace());
                        status.setRollbackOnly();
                    }
                }
            });
            return new RpcGetAdvanceCustomer(customer);
        }
    };
    /**
     * Поставить кастомера в очередь предварительно записанного. Проверить бронь, поставить или отказать.
     */
    final Task standAdvanceCheckAndStand = new Task(Uses.TASK_ADVANCE_CHECK_AND_STAND) {

        @Override
        public RpcStandInService process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);

            // Вытащим из базы предварительного кастомера
            final QAdvanceCustomer advCust = Spring.getInstance().getHt().get(QAdvanceCustomer.class, cmdParams.customerId);
            if (advCust == null || advCust.getId() == null || advCust.getAdvanceTime() == null) {
                QLog.l().logger().debug("не найден клиент по его ID=" + cmdParams.customerId);
                // Шлем отказ
                return new RpcStandInService(null, "Не верный номер предварительной записи.");
            }
            final GregorianCalendar gc = new GregorianCalendar();
            if (advCust != null) {
                gc.setTime(advCust.getAdvanceTime());
                gc.set(GregorianCalendar.HOUR_OF_DAY, gc.get(GregorianCalendar.HOUR_OF_DAY) - 1);
            }
            final GregorianCalendar gc1 = new GregorianCalendar();
            if (advCust != null) {
                gc1.setTime(advCust.getAdvanceTime());
                gc1.set(GregorianCalendar.MINUTE, gc1.get(GregorianCalendar.MINUTE) + 20);
            }
            if (advCust != null && new Date().before(gc1.getTime()) && new Date().after(gc.getTime())) {
                // Ставим кастомера
                //трем запись в таблице предварительных записей

                Spring.getInstance().getTt().execute(new TransactionCallbackWithoutResult() {

                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        try {
                            Spring.getInstance().getHt().delete(advCust);
                            QLog.l().logger().debug("Удалили предварителньную запись о кастомере.");
                        } catch (Exception ex) {
                            status.setRollbackOnly();
                            throw new ServerException("Ошибка при удалении \n" + ex.toString() + "\n" + ex.getStackTrace());
                        }
                    }
                });
                // создаем кастомера вызвав задание по созданию кастомера
                // загрузим задание
                final CmdParams params = new CmdParams();
                params.serviceId = advCust.getService().getId();
                params.password = "";
                params.priority = advCust.getPriority();
                final RpcStandInService txtCustomer = addCustomerTask.process(params, ipAdress, IP);

                return txtCustomer;
            } else {
                String answer;
                if (advCust == null) {
                    QLog.l().logger().trace("Не найдена предварительная запись по введеному коду ID = " + cmdParams.customerId);
                    answer = "Не найдена предварительная запись по введеному коду";
                } else {
                    QLog.l().logger().trace("Предваритело записанный клиент пришел не в свое время");
                    answer = "Предваритело записанный клиент пришел не в свое время";
                }
                // Шлем отказ
                return new RpcStandInService(null, answer);
            }
        }
    };
    /**
     * Получение списка отзывов.
     */
    final Task getResponseList = new Task(Uses.TASK_GET_RESPONSE_LIST) {

        @Override
        public RpcGetRespList process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetRespList(QResponseList.getInstance().getItems());
        }
    };
    /**
     * Регистрация отзыва.
     */
    final Task setResponseAnswer = new Task(Uses.TASK_SET_RESPONSE_ANSWER) {

        @Override
        public JsonRPC20 process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final JsonRPC20 rpc = new JsonRPC20();
            final QRespEvent event = new QRespEvent();
            event.setDate(new Date());
            event.setRespID(cmdParams.responseId);
            Spring.getInstance().getTt().execute(new TransactionCallbackWithoutResult() {

                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    try {
                        Spring.getInstance().getHt().saveOrUpdate(event);
                        QLog.l().logger().debug("Сохранили отзыв в базе.");
                    } catch (Exception ex) {
                        rpc.setError(new JsonRPC20Error(JsonRPC20Error.RESPONCE_NOT_SAVE, ex));
                        QLog.l().logger().error("Ошибка при сохранении \n" + ex.toString() + "\n" + ex.getStackTrace());
                        status.setRollbackOnly();
                    }
                }
            });
            return rpc;
        }
    };
    /**
     * Получение информационного дерева.
     */
    final Task getInfoTree = new Task(Uses.TASK_GET_INFO_TREE) {

        @Override
        public RpcGetInfoTree process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetInfoTree(QInfoTree.getInstance().getRoot());
        }
    };
    /**
     * Идентифицировать кастомера по его ID.
     */
    final Task getClientAuthorization = new Task(Uses.TASK_GET_CLIENT_AUTHORIZATION) {

        @Override
        public RpcGetAuthorizCustomer process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final Long authCustID = Long.parseLong(cmdParams.clientAuthId);
            // Вытащим из базы предварительного кастомера
            final QAuthorizationCustomer authCust = new QAuthorizationCustomer();
            Spring.getInstance().getHt().load(authCust, authCustID);
            if (authCust.getId() == null || authCust.getName() == null) {
                throw new ServerException("не найден клиент по его ID");
            }
            return new RpcGetAuthorizCustomer(authCust);
        }
    };
    /**
     * Получение списка результатов по окончанию работы пользователя с клиентом.
     */
    final Task getResultsList = new Task(Uses.TASK_GET_RESULTS_LIST) {

        @Override
        public RpcGetResultsList process(CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            return new RpcGetResultsList(QResultList.getInstance().getItems());
        }
    };
    /**
     * Изменение приоритета кастомеру
     */
    final Task setCustomerPriority = new Task(Uses.TASK_SET_CUSTOMER_PRIORITY) {

        @Override
        public RpcGetSrt process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            // Вытащим из базы предварительного кастомера
            final String num = cmdParams.clientAuthId.trim();
            String s = "";
            for (QService service : QServiceTree.getInstance().getNodes()) {
                if (service.changeCustomerPriorityByNumber(num, cmdParams.priority)) {
                    s = "Клиенту с номером \"" + num + "\" в услуге \"" + service.getName() + "\" изменен приоритет.";
                    break;
                }
            }
            return new RpcGetSrt("".equals(s) ? "Клиент по введенному номеру \"" + num + "\" не найден." : s);
        }
    };
    /**
     * Рестарт сервера из админки
     */
    final Task restartServer = new Task(Uses.TASK_RESTART) {

        @Override
        public JsonRPC20 process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            QServer.savePool();
            ServerEvents.getInstance().restartEvent();
            QPostponedList.getInstance().clear();
            QServer.loadPool();
            MainBoard.getInstance().refresh();
            return new JsonRPC20();
        }
    };
    /**
     * Рестарт главного табло из админки
     */
    final Task restarMainTablo = new Task(Uses.TASK_RESTART_MAIN_TABLO) {

        @Override
        public JsonRPC20 process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            MainBoard.getInstance().refresh();
            return new JsonRPC20();
        }
    };
    /**
     * Изменить бегущий текст на табло
     */
    final Task refreshRunningText = new Task(Uses.TASK_CHANGE_RUNNING_TEXT_ON_BOARD) {

        @Override
        public JsonRPC20 process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            if (MainBoard.getInstance() instanceof QIndicatorBoardMonitor) {
                final QIndicatorBoardMonitor mon = (QIndicatorBoardMonitor) MainBoard.getInstance();
                if (Uses.TAG_BOARD_TOP.equals(cmdParams.infoItemName)) {
                    mon.indicatorBoard.getTopRunningLabel().stop();
                    mon.indicatorBoard.getTopRunningLabel().setText("");
                    mon.indicatorBoard.getTopRunningLabel().setShowTime(false);
                    mon.indicatorBoard.getTopRunningLabel().setRunningText(cmdParams.textData);
                    mon.indicatorBoard.getTopRunningLabel().start();
                }
                if (Uses.TAG_BOARD_LEFT.equals(cmdParams.infoItemName)) {
                    mon.indicatorBoard.getLeftRunningLabel().stop();
                    mon.indicatorBoard.getLeftRunningLabel().setText("");
                    mon.indicatorBoard.getLeftRunningLabel().setShowTime(false);
                    mon.indicatorBoard.getLeftRunningLabel().setRunningText(cmdParams.textData);
                    mon.indicatorBoard.getLeftRunningLabel().start();
                }
                if (Uses.TAG_BOARD_RIGHT.equals(cmdParams.infoItemName)) {
                    mon.indicatorBoard.getRightRunningLabel().stop();
                    mon.indicatorBoard.getRightRunningLabel().setText("");
                    mon.indicatorBoard.getRightRunningLabel().setShowTime(false);
                    mon.indicatorBoard.getRightRunningLabel().setRunningText(cmdParams.textData);
                    mon.indicatorBoard.getRightRunningLabel().start();
                }
                if (Uses.TAG_BOARD_BOTTOM.equals(cmdParams.infoItemName)) {
                    mon.indicatorBoard.getBottomRunningLabel().stop();
                    mon.indicatorBoard.getBottomRunningLabel().setText("");
                    mon.indicatorBoard.getBottomRunningLabel().setShowTime(false);
                    mon.indicatorBoard.getBottomRunningLabel().setRunningText(cmdParams.textData);
                    mon.indicatorBoard.getBottomRunningLabel().start();
                }
            }
            return new JsonRPC20();
        }
    };
    /**
     * Запрос на изменение приоритетор оказываемых услуг от юзеров
     */
    final Task changeFlexPriority = new Task(Uses.TASK_CHANGE_FLEX_PRIORITY) {

        @Override
        public JsonRPC20 process(final CmdParams cmdParams, String ipAdress, byte[] IP) {
            super.process(cmdParams, ipAdress, IP);
            final QUser user = QUserList.getInstance().getById(cmdParams.userId);
            for (String str : cmdParams.textData.split("&")) {
                final String[] ss = str.split("=");
                user.getPlanService(Long.parseLong(ss[0])).setCoefficient(Integer.parseInt(ss[1]));
            }
            return new JsonRPC20();
        }
    };

//****************************************************************************
//********************* КОНЕЦ добавления в мап обработчиков заданий  *********
//****************************************************************************
//**********************************************************************************************
//**********************************   ОБРАБОТКА ЗАДАНИЙ  **************************************
//**********************************************************************************************    
    /**
     * Выполнение всех заданий, пришедших на обработку
     * @param rpc объект задания
     * @param ipAdress адрес того кто прислал задание
     * @param IP  адрес того кто прислал задание
     * @return объект результата выполнения задания
     */
    public Object doTask(JsonRPC20 rpc, String ipAdress, byte[] IP) {
        final long start = System.currentTimeMillis();
        if (!QLog.l().isDebug()) {
            System.out.println("Task processing: '" + rpc.getMethod());
        }
        QLog.l().logger().info("Обработка задания: '" + rpc.getMethod() + "'");
        if (tasks.get(rpc.getMethod()) == null) {
            throw new ServerException("В задании не верно указано название действия: '" + rpc.getMethod() + "'");
        }

        final Object result;
        // Вызов обработчика задания не синхронизирован
        // Синхронизация переехала внутрь самих обработчиков с помощью блокировок
        // Это сделано потому что появилось много заданий, которые не надо синхронизировать.
        // А то что необходимо синхронизировать, то синхронизится в самих обработчиках.
        result = tasks.get(rpc.getMethod()).process(rpc.getParams(), ipAdress, IP);

        QLog.l().logger().info("Задание завершено. Затрачено времени: " + new Double(System.currentTimeMillis() - start) / 1000 + " сек.");
        return result;
    }
}
