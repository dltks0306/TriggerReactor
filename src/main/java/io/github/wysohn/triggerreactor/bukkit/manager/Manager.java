/*******************************************************************************
 *     Copyright (C) 2017 wysohn
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package io.github.wysohn.triggerreactor.bukkit.manager;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.Listener;

import io.github.wysohn.triggerreactor.bukkit.main.TriggerReactor;

public abstract class Manager implements Listener{
    private static final List<Manager> managers = new ArrayList<Manager>();
    public static List<Manager> getManagers() {
        return managers;
    }

    protected final TriggerReactor plugin;

    public Manager(TriggerReactor plugin) {
        this.plugin = plugin;

        managers.add(this);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public abstract void reload();
    public abstract void saveAll();
}