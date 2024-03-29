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

import ru.apertum.qsystem.server.controller.IIndicatorBoard;

/**
 *
 * @author egorov
 */
public class MainBoard  {

    private MainBoard() {
    }

    public static IIndicatorBoard getInstance() {
        return MainBoardHolder.INSTANCE;
    }

    private static class MainBoardHolder {
        private static final IIndicatorBoard INSTANCE = (IIndicatorBoard) Spring.getInstance().getFactory().getBean("indicatorBoard");
    }
 }
