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
package ru.apertum.qsystem.common;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import javax.sound.sampled.*;
import javax.swing.*;
import java.lang.Thread.*;
import java.util.LinkedList;
import ru.apertum.qsystem.server.ServerProps;

/**
 * Класс проигрывания звуковых ресурсов и файлов.
 * Создает отдельный поток для каждого проигрыша, но игоает синхронизированно.
 * По этому все ресурсы проиграются друг за другом и это не будет тормозить основной поток.
 * Воспроизведение кучи мелких файлов глючит, накладываются др. на др.
 * @author Evgeniy Egorov
 */
public class SoundPlayer implements Runnable {

    public SoundPlayer(LinkedList<String> resourceList) {
        this.resourceList = resourceList;
    }
    /**
     * Тут храним имя ресурса для загрузки
     */
    private final LinkedList<String> resourceList;

    /**
     * Проиграть звуковой ресурс
     * @param resourceName имя проигрываемого ресурса
     */
    public static void play(String resourceName) {
        final LinkedList<String> resourceList = new LinkedList<>();
        resourceList.add(resourceName);
        play(resourceList);
    }

    /**
     * Проиграть набор звуковых ресурсов
     * @param resourceList список имен проигрываемых ресурсов
     */
    public static void play(LinkedList<String> resourceList) {
        // и запускаем новый вычислительный поток (см. ф-ю run())
        final Thread playThread = new Thread(new SoundPlayer(resourceList));
        //playThread.setDaemon(true);
        playThread.setPriority(Thread.NORM_PRIORITY);
        playThread.start();
    }

    public static void printAudioFormatInfo(AudioFormat audioformat) {
        System.out.println("*****************************************");
        System.out.println("Format: " + audioformat.toString());
        System.out.println("Encoding: " + audioformat.getEncoding());
        System.out.println("SampleRate:" + audioformat.getSampleRate());
        System.out.println("SampleSizeInBits: " + audioformat.getSampleSizeInBits());
        System.out.println("Channels: " + audioformat.getChannels());
        System.out.println("FrameSize: " + audioformat.getFrameSize());
        System.out.println("FrameRate: " + audioformat.getFrameRate());
        System.out.println("BigEndian: " + audioformat.isBigEndian());
        System.out.println("*****************************************\n");
    }

    /** Asks the user to select a file to play.
     * @return 
     */
    public File getFileToPlay() {
        File file = null;
        JFrame frame = new JFrame();
        JFileChooser chooser = new JFileChooser(".");
        int returnvalue = chooser.showDialog(frame, "Select File to Play");
        if (returnvalue == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
        }
        return file;
    }

    @Override
    public void run() {
        doSounds(this, resourceList);
    }
    /**
     * Листенер, срабатываюшщий при начале проигрывания семплов
     */
    private static ActionListener startListener = null;

    public static ActionListener getStartListener() {
        return startListener;
    }

    public static void setStartListener(ActionListener startListener) {
        SoundPlayer.startListener = startListener;
    }
    /**
     * Событие завершения проигрывания семплов
     */
    private static ActionListener finishListener = null;

    public static ActionListener getFinishListener() {
        return finishListener;
    }

    public static void setFinishListener(ActionListener finishListener) {
        SoundPlayer.finishListener = finishListener;
    }

    synchronized private static void doSounds(Object o, LinkedList<String> resourceList) {
        if (startListener != null) {
            startListener.actionPerformed(new ActionEvent(o, 1, "start do sounds"));
        }
        for (String res : resourceList) {
            doSound(o, res);
        }
        if (finishListener != null) {
            finishListener.actionPerformed(new ActionEvent(o, 1, "finish do sounds"));
        }
    }

    synchronized private static void doSound(Object o, String resourceName) {
        QLog.l().logger().debug("Пытаемся воспроизвести звуковой ресурс \"" + resourceName + "\"");
        AudioInputStream ais = null;
        try {
            ais = AudioSystem.getAudioInputStream(Object.class.getResource(resourceName));
            //get the AudioFormat for the AudioInputStream 
            AudioFormat audioformat = ais.getFormat();
            //printAudioFormatInfo(audioformat);
            //ULAW & ALAW format to PCM format conversion 
            if ((audioformat.getEncoding() == AudioFormat.Encoding.ULAW)
                    || (audioformat.getEncoding() == AudioFormat.Encoding.ALAW)) {
                AudioFormat newformat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        audioformat.getSampleRate(),
                        audioformat.getSampleSizeInBits() * 2,
                        audioformat.getChannels(),
                        audioformat.getFrameSize() * 2,
                        audioformat.getFrameRate(),
                        true);
                ais = AudioSystem.getAudioInputStream(newformat, ais);
                audioformat = newformat;
                //printAudioFormatInfo(audioformat);
            }
            //checking for a supported output line 
            DataLine.Info datalineinfo = new DataLine.Info(SourceDataLine.class, audioformat);
            if (!AudioSystem.isLineSupported(datalineinfo)) {
                System.out.println("Line matching " + datalineinfo + " is not supported.");
            } else {
                byte[] sounddata;
                try (SourceDataLine sourcedataline = (SourceDataLine) AudioSystem.getLine(datalineinfo)) {
                    sourcedataline.open(audioformat);
                    sourcedataline.start();
                    int framesizeinbytes = audioformat.getFrameSize();
                    int bufferlengthinframes = sourcedataline.getBufferSize() / 8;
                    int bufferlengthinbytes = bufferlengthinframes * framesizeinbytes;
                    sounddata = new byte[bufferlengthinbytes];
                    int numberofbytesread = 0;
                    while ((numberofbytesread = ais.read(sounddata)) != -1) {
                        sourcedataline.write(sounddata, 0, numberofbytesread);
                    }
                    int frPos = -1;
                    while (frPos != sourcedataline.getFramePosition()) {
                        frPos = sourcedataline.getFramePosition();
                        Thread.sleep(100);
                    }
                }
                sounddata = null;
            }

            //printAudioFormatInfo(audioformat);
        } catch (InterruptedException ex) {
            QLog.l().logger().error("InterruptedException: " + ex);
        } catch (LineUnavailableException lue) {
            QLog.l().logger().error("LineUnavailableException: " + lue.toString());
        } catch (UnsupportedAudioFileException uafe) {
            QLog.l().logger().error("UnsupportedAudioFileException: " + uafe.toString());
        } catch (IOException ioe) {
            QLog.l().logger().error("IOException: " + ioe.toString());
        } finally {
            try {
                if (ais != null) {
                    ais.close();
                }
            } catch (IOException ex) {
                QLog.l().logger().error("IOException при освобождении входного потока медиаресурса: " + ex);
            }
        }
    }

    /**
     * Разбить фразу на звуки и сформировать набор файлов для воспроизведения.
     * @param path путь, где лежать звуковые ресурсы, это могут быть файлы на диске или ресурсы в jar
     * @param phrase фраза для разбора 
     * @return список файлов для воспроизведения фразы
     */
    private static LinkedList<String> toSound(String path, String phrase) {
        final LinkedList<String> res = new LinkedList<>();
        for (int i = 0; i < phrase.length(); i++) {

            String elem = phrase.substring(i, i + 1);
            if (isNum(phrase.charAt(i))) {
                String ss = elem;
                if (i != 0 && isNum(phrase.charAt(i - 1))) {
                    ss = "_" + ss;
                }
                int n = i + 1;
                boolean suff = false;
                while (n < phrase.length() && isNum(phrase.charAt(n))) {
                    ss = ss + "0";
                    if ('0' != phrase.charAt(n)) {
                        suff = true;
                    }
                    n++;
                }
                if (suff) {
                    ss = ss + "_";
                } else {
                    i = n - 1;
                }
                elem = ss;
                if (elem.indexOf("_0") != -1 && elem.indexOf("0_") != -1) {
                    continue;
                }
                if (isZero(elem)) {
                    elem = "0";
                }
                if (elem.endsWith("10_")) {
                    char[] ch = new char[1];
                    ch[0] = phrase.charAt(i + 1);
                    elem = elem.replaceFirst("10_", "1" + new String(ch));
                    i++;
                }
            }

            final String file = path + elem.toLowerCase() + ".wav";
            //System.out.println(nom.substring(i, i + 1) + " - " + file);
            res.add(file);

        }
        return res;
    }

    /**
     * Разбить фразу на звуки и сформировать набор файлов для воспроизведения. Упрощенный вариант.
     * @param path путь, где лежать звуковые ресурсы, это могут быть файлы на диске или ресурсы в jar
     * @param phrase фраза для разбора
     * @return список файлов для воспроизведения фразы
     */
    public static LinkedList<String> toSoundSimple(String path, String phrase) {
        final LinkedList<String> res = new LinkedList<>();
        for (int i = 0; i < phrase.length(); i++) {

            String elem = phrase.substring(i, i + 1);
            if (isNum(phrase.charAt(i))) {

                if (!isZero(elem)) {
                    int j = i + 1;
                    while (j < phrase.length() && isNum(phrase.charAt(j))) {
                        elem = elem + "0";
                        j++;
                    }
                }
                if ("10".equals(elem)) {
                    elem = phrase.substring(i, i + 2);
                    i++;
                }

            }
            if (!isZero(elem)) {
                String fileName = elem;
                if (isRus(elem)) {
                    fileName = reRus(elem.toLowerCase());
                }
                final String file = path + fileName.toLowerCase() + ".wav";
                //System.out.println(elem + " - " + file);
                res.add(file);
            }

        }
        return res;
    }

    private static boolean isRus(String elem) {
        return "йцукенгшщзхъфывапролджэячсмитьбю".indexOf(elem.toLowerCase()) != -1;
    }

    private static String reRus(String elem) {
        final String is__ru = "й ц у к е н г ш щ з х ъ ф ы в а п р о л д ж э я ч с м и т ь б ю ё ";
        final String not_ru = "iic u k e n g shghz x zzf yyv a p r o l d jzeeiachs m i t ccb iuio";
        int pos = is__ru.indexOf(elem.toLowerCase());
        String ns = not_ru.substring(pos, pos + 2).trim().toLowerCase();
        return "_rus_" + ns;
    }

    private static boolean isNum(char elem) {
        return '1' == elem || '2' == elem || '3' == elem || '4' == elem || '5' == elem || '6' == elem || '7' == elem || '8' == elem || '9' == elem || '0' == elem;
    }

    private static boolean isZero(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!('0' == str.charAt(i) || '_' == str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Проговорить вызов клиента голосом
     * @param clientNumber номер вызываемого клиента
     * @param pointNumber  номер кабинета, куда вызвали
     */
    public static void inviteClient(String clientNumber, String pointNumber, boolean isFirst) {
        if (ServerProps.getInstance().getProps().getSound() == 0) {
            return;
        }
        final LinkedList<String> res = new LinkedList<>();
        // путь к звуковым файлам
        final String voice;
        switch (ServerProps.getInstance().getProps().getVoice()) {
            case 1:
                voice = "Alyona/";
                break;
            case 2:
                voice = "Nikolay/";
                break;
            case 3:
                voice = "Olga/";
                break;
            default:
                voice = "";
        }

        final String path = "/ru/apertum/qsystem/server/sound/" + voice;
        if ((isFirst && ServerProps.getInstance().getProps().getSound() == 2) || ServerProps.getInstance().getProps().getSound() == 1 || ServerProps.getInstance().getProps().getSound() == 3) {
            res.add(path + "ding.wav");
        }

        if (ServerProps.getInstance().getProps().getSound() == 3 || (!isFirst && ServerProps.getInstance().getProps().getSound() == 2)) {
            res.add(path + "client.wav");

            res.addAll(toSoundSimple(path, clientNumber));

            switch (ServerProps.getInstance().getProps().getPoint()) {
                case 0:
                    res.add(path + "tocabinet.wav");
                    break;
                case 1:
                    res.add(path + "towindow.wav");
                    break;
                case 2:
                    res.add(path + "tostoika.wav");
                    break;
                default:
                    res.add(path + "towindow.wav");
            }

            res.addAll(toSoundSimple(path, pointNumber));
        }
        SoundPlayer.play(res);
    }
}
