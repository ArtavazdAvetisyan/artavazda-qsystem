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
package ru.apertum.qsystem.common.cmd;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import ru.apertum.qsystem.server.model.QAdvanceCustomer;

/**
 *
 * @author Evgeniy Egorov
 */
public class RpcGetAdvanceCustomer extends JsonRPC20 {

    public RpcGetAdvanceCustomer() {
    }
    
    @Expose
    @SerializedName("auth_customer")
    private QAdvanceCustomer result;

    public void setResult(QAdvanceCustomer result) {
        this.result = result;
    }

    public QAdvanceCustomer getResult() {
        return result;
    }

    public RpcGetAdvanceCustomer(QAdvanceCustomer result) {
        this.result = result;
    }

}
