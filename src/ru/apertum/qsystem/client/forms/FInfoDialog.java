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
package ru.apertum.qsystem.client.forms;

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.MemoryImageSource;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.EtchedBorder;
import org.jdesktop.application.Action;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;
import ru.apertum.qsystem.QSystem;
import ru.apertum.qsystem.common.Uses;
import ru.apertum.qsystem.common.QLog;
import ru.apertum.qsystem.common.model.ATalkingClock;
import ru.apertum.qsystem.server.model.infosystem.QInfoItem;

/**
 * Created on 18.09.2009, 11:33:46
 * Диалог постановки в очередь по коду предварительной регистрации
 * Имеет метод для осуществления всех действий. Вся логика инкапсулирована в этом классе.
 * Должен уметь работать с комовским сканером.
 * @author Evgeniy Egorov
 */
public class FInfoDialog extends javax.swing.JDialog {

    private static FInfoDialog infoDialog;

    /** Creates new form FStandAdvance
     * @param parent
     * @param modal
     */
    public FInfoDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        infoDialog = this;
        initComponents();

    }

    public static void setRoot(QInfoItem rootEl) {
        root = rootEl;
        preLevel = rootEl;
    }
    private static Long result = null;
    private static int delay = 10000;
    /**
     * Корень справочной системы
     */
    private static QInfoItem root;
    /**
     * Предыдущий уровень кнопок
     */
    private static QInfoItem preLevel;
    /**
     * Текущий уровень кнопок
     */
    private static QInfoItem level;
    private static ResourceMap localeMap = null;

    private static String getLocaleMessage(String key) {
        if (localeMap == null) {
            localeMap = Application.getInstance(QSystem.class).getContext().getResourceMap(FInfoDialog.class);
        }
        return localeMap.getString(key);
    }

    /**
     * Статический метод который показывает модально диалог чтения информации.
     * @param parent фрейм относительно которого будет модальность
     * @param respList XML-дерево информации
     * @param modal модальный диалог или нет
     * @param fullscreen растягивать форму на весь экран и прятать мышку или нет
     * @param delay задержка перед скрытием диалога. если 0, то нет автозакрытия диалога
     * @return XML-описание результата предварительной записи, по сути это номерок. если null, то отказались от предварительной записи
     */
    public static Long showResponseDialog(Frame parent, QInfoItem respList, boolean modal, boolean fullscreen, int delay) {
        FInfoDialog.delay = delay;
        QLog.l().logger().info("Чтение информации");
        if (infoDialog == null) {
            infoDialog = new FInfoDialog(parent, modal);
            infoDialog.setTitle(getLocaleMessage("dialog.title"));
        }
        FInfoDialog.setRoot(respList);
        FInfoDialog.result = null;
        Uses.setLocation(infoDialog);
        if (!(QLog.l().isDebug() || QLog.l().isDemo() && !fullscreen)) {
            Uses.setFullSize(infoDialog);
            int[] pixels = new int[16 * 16];
            Image image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(16, 16, pixels, 0, 16));
            Cursor transparentCursor = Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "invisibleCursor");
            infoDialog.setCursor(transparentCursor);

        }
        infoDialog.showLevel(FInfoDialog.root);
        if (infoDialog.clockBack.isActive()) {
            infoDialog.clockBack.stop();
        }
        infoDialog.clockBack.start();
        infoDialog.setVisible(true);
        return result;
    }

    private void showLevel(QInfoItem level) {
        infoDialog.panelMain.removeAll();
        infoDialog.panelMain.repaint();
        if (level.getParent() == null && level != root) {
            level.setParent(FInfoDialog.level);
        }
        FInfoDialog.level = level;
        buttonPrint.setVisible(level.isLeaf() && level.getTextPrint() != null && !level.getTextPrint().isEmpty());
        if (level.isLeaf()) {
            final JLabel label = new JLabel(Uses.prepareAbsolutPathForImg(level.getHTMLText()));
            final GridBagLayout gl = new GridBagLayout();
            final GridBagConstraints c = new GridBagConstraints();
            gl.setConstraints(label, c);
            panelMain.setLayout(gl);
            panelMain.add(label);
        } else {
            int delta = 10;
            switch (Toolkit.getDefaultToolkit().getScreenSize().width) {
                case 640:
                    delta = 10;
                    break;
                case 800:
                    delta = 20;
                    break;
                case 1024:
                    delta = 30;
                    break;
                case 1280:
                    delta = 40;
                    break;
                case 1600:
                    delta = 50;
                    break;
            }
            int cols = 3;
            int rows = 5;
            if (level.getChildCount() < 4) {
                cols = 1;
                rows = 3;
            }
            if (level.getChildCount() > 3 && level.getChildCount() < 11) {
                cols = 2;
                rows = Math.round(new Float(level.getChildCount()) / 2);
            }
            if (level.getChildCount() > 10) {
                cols = 3;
                rows = Math.round(new Float(0.3) + level.getChildCount() / 3);
            }
            infoDialog.panelMain.setLayout(new GridLayout(rows, cols, delta, delta / 2));
            for (QInfoItem item : level.getChildren()) {
                final InfoButton button = new InfoButton(item);
                infoDialog.panelMain.add(button);
            }
            if (level != root) {
                preLevel = level.getParent();
            } else {
                preLevel = root;
            }
            setSize(getWidth() + s(), getHeight());
        }
    }
    private static int s = 1;

    private static int s() {
        s = (-1) * s;
        return s;
    }

    /**
     * Кнопка для навигации по дереву информации и для вывода этой информации ей в капшен
     */
    private static class InfoButton extends JButton {

        final QInfoItem el;

        public InfoButton(final QInfoItem el) {
            this.el = el;
            setText(Uses.prepareAbsolutPathForImg(el.getHTMLText()));
            setBorder(new EtchedBorder(EtchedBorder.LOWERED));
            addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!el.isLeaf() || (el.isLeaf() && el.getTextPrint() != null && !"".equals(el.getTextPrint()))) {
                        infoDialog.showLevel(el);
                    }
                }
            });
        }
    }
    /**
     * Таймер, по которому будем выходить в корень меню.
     */
    public ATalkingClock clockBack = new ATalkingClock(delay, 1) {

        @Override
        public void run() {
            setVisible(false);
        }
    };

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelAll = new ru.apertum.qsystem.client.model.QPanel();
        panelUp = new ru.apertum.qsystem.client.model.QPanel();
        LabelCaption2 = new javax.swing.JLabel();
        panelBottom = new ru.apertum.qsystem.client.model.QPanel();
        jButton2 = new javax.swing.JButton();
        buttonInRoot = new javax.swing.JButton();
        buttonBack = new javax.swing.JButton();
        buttonPrint = new javax.swing.JButton();
        panelMain = new ru.apertum.qsystem.client.model.QPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setName("Form"); // NOI18N
        setUndecorated(true);

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(ru.apertum.qsystem.QSystem.class).getContext().getResourceMap(FInfoDialog.class);
        panelAll.setBackground(resourceMap.getColor("panelAll.background")); // NOI18N
        panelAll.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        panelAll.setName("panelAll"); // NOI18N

        panelUp.setBorder(new javax.swing.border.MatteBorder(null));
        panelUp.setCycle(java.lang.Boolean.FALSE);
        panelUp.setEndColor(resourceMap.getColor("panelUp.endColor")); // NOI18N
        panelUp.setEndPoint(new java.awt.Point(0, 70));
        panelUp.setGradient(java.lang.Boolean.TRUE);
        panelUp.setName("panelUp"); // NOI18N
        panelUp.setStartColor(resourceMap.getColor("panelUp.startColor")); // NOI18N
        panelUp.setStartPoint(new java.awt.Point(0, -50));

        LabelCaption2.setFont(resourceMap.getFont("LabelCaption2.font")); // NOI18N
        LabelCaption2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        LabelCaption2.setText(resourceMap.getString("LabelCaption2.text")); // NOI18N
        LabelCaption2.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        LabelCaption2.setName("LabelCaption2"); // NOI18N

        javax.swing.GroupLayout panelUpLayout = new javax.swing.GroupLayout(panelUp);
        panelUp.setLayout(panelUpLayout);
        panelUpLayout.setHorizontalGroup(
            panelUpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelUpLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LabelCaption2, javax.swing.GroupLayout.DEFAULT_SIZE, 1004, Short.MAX_VALUE)
                .addContainerGap())
        );
        panelUpLayout.setVerticalGroup(
            panelUpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelUpLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(LabelCaption2, javax.swing.GroupLayout.DEFAULT_SIZE, 59, Short.MAX_VALUE)
                .addContainerGap())
        );

        panelBottom.setBorder(new javax.swing.border.MatteBorder(null));
        panelBottom.setEndPoint(new java.awt.Point(0, 100));
        panelBottom.setGradient(java.lang.Boolean.TRUE);
        panelBottom.setName("panelBottom"); // NOI18N
        panelBottom.setStartColor(resourceMap.getColor("panelBottom.startColor")); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(ru.apertum.qsystem.QSystem.class).getContext().getActionMap(FInfoDialog.class, this);
        jButton2.setAction(actionMap.get("exit")); // NOI18N
        jButton2.setFont(resourceMap.getFont("jButton2.font")); // NOI18N
        jButton2.setIcon(resourceMap.getIcon("jButton2.icon")); // NOI18N
        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
        jButton2.setName("jButton2"); // NOI18N
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        buttonInRoot.setFont(resourceMap.getFont("buttonInRoot.font")); // NOI18N
        buttonInRoot.setIcon(resourceMap.getIcon("buttonInRoot.icon")); // NOI18N
        buttonInRoot.setText(resourceMap.getString("buttonInRoot.text")); // NOI18N
        buttonInRoot.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
        buttonInRoot.setName("buttonInRoot"); // NOI18N
        buttonInRoot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonInRootActionPerformed(evt);
            }
        });

        buttonBack.setFont(resourceMap.getFont("buttonBack.font")); // NOI18N
        buttonBack.setIcon(resourceMap.getIcon("buttonBack.icon")); // NOI18N
        buttonBack.setText(resourceMap.getString("buttonBack.text")); // NOI18N
        buttonBack.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
        buttonBack.setName("buttonBack"); // NOI18N
        buttonBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonBackActionPerformed(evt);
            }
        });

        buttonPrint.setAction(actionMap.get("printInfo")); // NOI18N
        buttonPrint.setFont(resourceMap.getFont("buttonPrint.font")); // NOI18N
        buttonPrint.setIcon(resourceMap.getIcon("buttonPrint.icon")); // NOI18N
        buttonPrint.setText(resourceMap.getString("buttonPrint.text")); // NOI18N
        buttonPrint.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
        buttonPrint.setName("buttonPrint"); // NOI18N

        javax.swing.GroupLayout panelBottomLayout = new javax.swing.GroupLayout(panelBottom);
        panelBottom.setLayout(panelBottomLayout);
        panelBottomLayout.setHorizontalGroup(
            panelBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelBottomLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 108, Short.MAX_VALUE)
                .addComponent(buttonPrint, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(buttonInRoot, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(buttonBack, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        panelBottomLayout.setVerticalGroup(
            panelBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBottomLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(buttonBack, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE)
                        .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE)
                        .addComponent(buttonInRoot, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE))
                    .addComponent(buttonPrint, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE))
                .addContainerGap())
        );

        panelMain.setBackground(resourceMap.getColor("panelMain.background")); // NOI18N
        panelMain.setBorder(new javax.swing.border.MatteBorder(null));
        panelMain.setName("panelMain"); // NOI18N

        javax.swing.GroupLayout panelMainLayout = new javax.swing.GroupLayout(panelMain);
        panelMain.setLayout(panelMainLayout);
        panelMainLayout.setHorizontalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1024, Short.MAX_VALUE)
        );
        panelMainLayout.setVerticalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 321, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout panelAllLayout = new javax.swing.GroupLayout(panelAll);
        panelAll.setLayout(panelAllLayout);
        panelAllLayout.setHorizontalGroup(
            panelAllLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panelUp, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(panelBottom, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(panelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        panelAllLayout.setVerticalGroup(
            panelAllLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelAllLayout.createSequentialGroup()
                .addComponent(panelUp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panelBottom, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panelAll, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panelAll, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        /* result = null;
        if (clockBack.isActive()) {
        clockBack.stop();
        }
        setVisible(false);*/
    }//GEN-LAST:event_jButton2ActionPerformed

    private void buttonInRootActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonInRootActionPerformed
        showLevel(root);
        if (infoDialog.clockBack.isActive()) {
            infoDialog.clockBack.stop();
        }
        infoDialog.clockBack.start();
    }//GEN-LAST:event_buttonInRootActionPerformed

    private void buttonBackActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonBackActionPerformed
        if (level.isLeaf()) {
            showLevel(level.getParent());
        } else {
            showLevel(preLevel);
        }
        if (infoDialog.clockBack.isActive()) {
            infoDialog.clockBack.stop();
        }
        infoDialog.clockBack.start();
    }

    @Action
    public void exit() {
        result = null;
        if (clockBack.isActive()) {
            clockBack.stop();
        }
        setVisible(false);
    }//GEN-LAST:event_buttonBackActionPerformed

    @Action
    public void printInfo() {
        QLog.l().logger().info("Печать информации");
        // Узнать, есть ли информация для печати
        if (level.getTextPrint() != null && !level.getTextPrint().isEmpty()) {
            FWelcome.printPreInfoText(level.getTextPrint());
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel LabelCaption2;
    private javax.swing.JButton buttonBack;
    private javax.swing.JButton buttonInRoot;
    private javax.swing.JButton buttonPrint;
    private javax.swing.JButton jButton2;
    private ru.apertum.qsystem.client.model.QPanel panelAll;
    private ru.apertum.qsystem.client.model.QPanel panelBottom;
    private ru.apertum.qsystem.client.model.QPanel panelMain;
    private ru.apertum.qsystem.client.model.QPanel panelUp;
    // End of variables declaration//GEN-END:variables
}
