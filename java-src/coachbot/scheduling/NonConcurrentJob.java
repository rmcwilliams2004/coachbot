/*
 * Copyright (c) 2017, Courage Labs, LLC.
 *
 * This file is part of CoachBot.
 *
 * CoachBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CoachBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with CoachBot.  If not, see <http://www.gnu.org/licenses/>.
 */

package coachbot.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;

@DisallowConcurrentExecution
public interface NonConcurrentJob extends Job {
}
