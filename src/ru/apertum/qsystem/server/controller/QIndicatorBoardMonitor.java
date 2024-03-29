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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import javax.imageio.ImageIO;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import ru.apertum.qsystem.client.forms.AFBoardRedactor;
import ru.apertum.qsystem.client.forms.FBoardConfig;
import ru.apertum.qsystem.client.forms.FIndicatorBoard;
import ru.apertum.qsystem.common.CustomerState;
import ru.apertum.qsystem.common.QLog;
import ru.apertum.qsystem.common.SoundPlayer;
import ru.apertum.qsystem.common.exceptions.ServerException;

/**
 * Вывод информации на мониторы.
 * Класс-менеджер вывода информации на общее табло в виде монитора.
 * @author Evgeniy Egorov
 */
public class QIndicatorBoardMonitor extends AIndicatorBoard {

    protected FIndicatorBoard indicatorBoard = null;
    protected String configFile;

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        final String err = ("/".equals(File.separator)) ? "\\" : "/";
        while (configFile.indexOf(err) != -1) {
            configFile = configFile.replace(err, File.separator);
        }
        this.configFile = configFile;
    }

    /**
     * Заткнуть звук видеороликов для озвучки вызова голосом.
     * @param mute
     */
    public void setMute(boolean mute) {
        if (indicatorBoard != null) {
            indicatorBoard.setMute(mute);
        }
    }

    /**
     * Создадим форму, спозиционируем, сконфигурируем и покажем
     * @param configFilePath файл конфигурации табло, приезжает из Spring
     */
    protected void initIndicatorBoard() {
        if (indicatorBoard == null) {
            final Element rootParams = getConfig();
            indicatorBoard = FIndicatorBoard.getIndicatorBoard(rootParams);
            if (indicatorBoard == null) {
                QLog.l().logger().warn("Табло не демонстрируется. Отключено в настройках.");
                return;
            }
            try {
                indicatorBoard.setIconImage(ImageIO.read(QIndicatorBoardMonitor.class.getResource("/ru/apertum/qsystem/client/forms/resources/recent.png")));
            } catch (IOException ex) {
                System.err.println(ex);
            }
            // Определим форму нв монитор
            indicatorBoard.toPosition(QLog.l().isDebug(), Integer.parseInt(rootParams.attributeValue("x")), Integer.parseInt(rootParams.attributeValue("y")));

            setLinesCount(indicatorBoard.getLinesCount());
            setPause(indicatorBoard.getPause());
            if (records.size() != 0) {
                showOnBoard(new LinkedHashSet<>(records.values()));
            }

            java.awt.EventQueue.invokeLater(new Runnable() {

                @Override
                public void run() {
                    indicatorBoard.setVisible(true);
                }
            });
        }
    }

    public QIndicatorBoardMonitor() {
        QLog.l().logger().info("Создание табло для телевизоров или мониторов.");
    }

    @Override
    protected void showOnBoard(LinkedHashSet<Record> records) {
        int i = 0;
        for (Record rec : records) {
            indicatorBoard.printRecord(i++, rec.customerNumber, rec.point, rec.getState() == CustomerState.STATE_INVITED ? 0 : -1);
        }
        for (int t = i; t < getLinesCount(); t++) {
            indicatorBoard.printRecord(t, "", "", -1);
        }
        markShowed(records);
    }

    /**
     * 
     * @param record
     * @deprecated при конфигурации с мониторами в качестве табло пользовательские моники подключаются к пользовательским компам.
     */
    @Deprecated
    @Override
    protected void showToUser(Record record) {
    }

    @Override
    public Element getConfig() {
        final File boardFile = new File(getConfigFile());
        if (boardFile.exists()) {
            try {
                return new SAXReader(false).read(boardFile).getRootElement();
            } catch (DocumentException ex) {
                QLog.l().logger().error("Невозможно прочитать файл конфигурации главного табло. " + ex.getMessage());
                return DocumentHelper.createElement("Ответ");
            }
        } else {
            QLog.l().logger().warn("Файл конфигурации главного табло \"" + configFile + "\" не найден. ");
            return DocumentHelper.createElement("Ответ");
        }
    }

    @Override
    public void saveConfig(Element element) {
        // в темповый файл
        final FileOutputStream fos;
        try {
            fos = new FileOutputStream(getConfigFile());
        } catch (FileNotFoundException ex) {
            throw new ServerException("Не возможно создать файл конфигурации главного табло. " + ex.getMessage());
        }
        try {
            fos.write(element.asXML().getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (IOException ex) {
            throw new ServerException("Не возможно сохранить изменения в поток при сохранении файла конфигурации главного табло." + ex.getMessage());
        }
    }

    @Override
    public AFBoardRedactor getRedactor() {
        if (boardConfig == null) {
            boardConfig = FBoardConfig.getBoardConfig(null, false);
        }
        return boardConfig;
    }
    /**
     * Используемая ссылка на диалоговое окно. Singleton
     */
    private static FBoardConfig boardConfig;

    @Override
    public void showBoard() {
        // Для прерывания звука в роликах при звуковом оповещении.
        SoundPlayer.setStartListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                setMute(true);
            }
        });
        SoundPlayer.setFinishListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                setMute(false);
            }
        });
        initIndicatorBoard();
    }

    /**
     * Выключить информационное табло.
     */
    @Override
    public synchronized void close() {
        super.close();
        if (indicatorBoard != null) {
            indicatorBoard.closeVideo();
            indicatorBoard.setVisible(false);
            indicatorBoard = null;
        }
    }

    @Override
    public void refresh() {
        close();
        indicatorBoard = null;
        initIndicatorBoard();
    }

    @Override
    public void clear() {
        records.clear();
        showOnBoard(new LinkedHashSet(records.values()));
    }
}
