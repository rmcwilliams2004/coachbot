;
; Copyright (c) 2017, Courage Labs, LLC.
;
; This file is part of CoachBot.
;
; CoachBot is free software: you can redistribute it and/or modify
; it under the terms of the GNU Affero General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; CoachBot is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU Affero General Public License for more details.
;
; You should have received a copy of the GNU Affero General Public License
; along with CoachBot.  If not, see <http://www.gnu.org/licenses/>.
;

(ns coachbot.question-management
  (:require [coachbot.env :as env]
            [clojure.pprint :as pprint]
            [taoensso.timbre :as log])
  (:import (java.io FileInputStream)
           (org.activiti.engine ProcessEngine ProcessEngineConfiguration)
           (org.activiti.engine.impl.cfg StandaloneProcessEngineConfiguration)
           (org.activiti.engine.delegate JavaDelegate)
           (org.activiti.engine.delegate.event ActivitiEventListener))
  (:gen-class))

(defn- bean-to-str [item]
  (with-out-str (pprint/pprint (bean item))))

(deftype SendMessageTask []
  JavaDelegate
  (execute [_ execution]
    (let [{:strs [stuff things]} (.getVariables execution)]
      (log/infof "Send message: stuff=%s, things=%s" stuff things)
      (.setVariable execution "done" "yay!"))
    "success"))

(comment
  (def event-listener
    (reify ActivitiEventListener
      (onEvent [_ event] (log/debugf "Event: %s" (bean-to-str event)))))

  (def cfg (doto (StandaloneProcessEngineConfiguration.)
             (.setJdbcUrl "jdbc:h2:mem:activiti")
             (.setJdbcUsername "sa")
             (.setJdbcPassword "")
             (.setJdbcDriver "org.h2.Driver")
             (.setDatabaseSchemaUpdate
               ProcessEngineConfiguration/DB_SCHEMA_UPDATE_TRUE)
             (.setJobExecutorActivate false)
             (.setEventListeners [event-listener])))

  (def hello-bpm
    (slurp "resources/coachbot/question_management/HelloProcess.bpmn"))

  (def engine (.buildProcessEngine cfg))

  (def repository-service (.getRepositoryService engine))

  (def runtime-service (.getRuntimeService engine))

  (defn get-process-id []
    (let [deployed-processes (-> repository-service
                                 (.createProcessDefinitionQuery)
                                 (.list))
          current-process (first deployed-processes)]
      (.getId current-process)))

  (def execution (.startProcessInstanceById runtime-service (get-process-id)
                                            {"stuff" "bits"
                                             "things" [3 4 5]}))

  (def execution-info {:id (.getId execution)
                       :activity-id (.getActivityId execution)
                       :description (.getDescription execution)
                       :name (.getName execution)
                       :process-instance-id (.getProcessInstanceId execution)
                       :parent-id (.getParentId execution)
                       :super-execution-id (.getSuperExecutionId execution)
                       :tenant-id (.getTenantId execution)
                       :ended? (.isEnded execution)
                       :suspended? (.isSuspended execution)
                       :variables (.getVariables execution)})

  (-> repository-service
      (.createDeployment)
      (.addString "HelloProcess.bpmn" hello-bpm)
      (.deploy))


  (defn method-info [m]
    {:name (.getName m)
     :return-type (.getReturnType m)
     :param-types (map #(.getName %) (.getParameterTypes m))})

  (log/with-log-level
    :info
    (let [task-service (.getTaskService engine)

          running-tasks (-> task-service
                            (.createTaskQuery)
                            (.executionId (:id execution-info))
                            (.list))
          task (-> running-tasks
                   first)


          task-info {:type "task"
                     :id (.getId task)
                     :execution-id (.getExecutionId task)
                     :local-vars (.getTaskLocalVariables task)
                     :assignee (.getAssignee task)
                     :form-key (.getFormKey task)
                     :description (.getDescription task)
                     :name (.getName task)
                     :task-def-key (.getTaskDefinitionKey task)
                     :process-def-id (.getProcessDefinitionId task)
                     :process-instance-id (.getProcessInstanceId task)}
          ]






      (as-> (.getDeployedProcessDefinition repository-service
                                           (:process-def-id task-info)) x

            (.findActivity x (:task-def-key task-info))
            (.getProperty x "taskDefinition")
            (.getClass x)
            (.getMethods x)
            (map method-info x)
            (sort-by :name x)
            (clojure.pprint/print-table x))

      ))

  )