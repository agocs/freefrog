;
; Copyright © 2014 Courage Labs
;
; This file is part of Freefrog.
;
; Freefrog is free software: you can redistribute it and/or modify
; it under the terms of the GNU Affero General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; Freefrog is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU Affero General Public License for more details.
;
; You should have received a copy of the GNU Affero General Public License
; along with this program.  If not, see <http://www.gnu.org/licenses/>.
;

;;; # Governance Encoding #
;;; Defines how governance can be manipulated, and is intended
;;; to comply to the
;;; [Holacracy Constitution v4.0](http://holacracy.org/sites/default/files/resources/holacracy_constitution_v4.0.pdf)
;;;
;;; Designed to provide all manipulation functions for Holacracy governance
;;; structures. It is not recommended that the structures
;;; generated/manipulated by this namespace be manually edited outside of
;;; this namespace for any purpose.
;;;
;;; This would cause difficulties tracking down the source of a structure
;;; should one need to do so. It also adds the benefit of having all logic in
;;; one place so one can more easily reason about it.

(ns freefrog.governance
  (:require [clojure.set :as s]))

(defn- validate-not
  "Throws exception with given err-msg if invalid? is true."
  [invalid? err-msg]
  (when invalid? (throw (IllegalArgumentException. err-msg))))

(defn anchor-circle
  "Create a new anchor circle.  If given lead-link-* parameters, will
   assign a lead link."
  ([name]
   (validate-not (empty? name) "Name may not be empty")
   {:name name})

  ([name lead-link-name lead-link-email]
   (validate-not (or (empty? name) (empty? lead-link-name)
                      (empty? lead-link-email)) "No parameters may be empty")
   (let [anchor-circle (anchor-circle name)]
     (assoc anchor-circle :lead-link
            {:name lead-link-name :email lead-link-email}))))

(defn- assoc-if [map key value]
  "Associate a value with a key only if the value is non-nil."
  (if value (assoc map key value) map))

(defn- make-role [purpose domains accountabilities]
  (-> {}
      (assoc-if :purpose purpose)
      (assoc-if :domains domains)
      (assoc-if :accountabilities accountabilities)))

(defn- validate-role-name [role-name]
  (validate-not (empty? role-name) "Name may not be empty"))

(defn add-role
  "Adds a role to a circle.  The role may not conflict with an existing role.
   role-name may not be empty."
  ([circle role-name purpose]
   (add-role circle role-name purpose nil nil))
  ([circle role-name purpose domains accountabilities]
   (validate-role-name role-name)
   (validate-not (get-in circle [:roles role-name])
                  (str "Role already exists: " role-name))
   (let [circle (if (contains? circle :roles)
                  circle
                  (assoc circle :roles {}))]
     (update-in circle [:roles] assoc role-name
                (make-role purpose domains accountabilities)))))

(defn- validate-role-exists [circle role-name]
  (validate-not (empty? (get-in circle [:roles role-name]))
                 (str "Role not found: " role-name)))

(defn- validate-role-updates [circle role-name]
  "Checks that the role name is not empty and that it exists in the circle."
  (validate-role-name role-name)
  (validate-role-exists circle role-name))

(defn remove-role
  "Remove a role from a circle."
  [circle role-name]
  (validate-role-updates circle role-name)
  (let [result (update-in circle [:roles] dissoc role-name)]
    (if (empty? (:roles result))
      (dissoc result :roles)
      result)))

(defn rename-role
  "Rename a role in the given circle."
  [circle role-name new-name]
  (validate-role-updates circle role-name)
  (update-in circle [:roles]
             s/rename-keys {role-name new-name}))

(defn update-role-purpose
  "Update the purpose of a role in the given circle."
  [circle role-name new-purpose]
  (validate-role-updates circle role-name)
  (if (empty? new-purpose)
    (update-in circle [:roles role-name] dissoc :purpose)
    (update-in circle [:roles role-name] assoc :purpose new-purpose)))

(defn- get-things [circle role-name type]
  (get-in circle [:roles role-name type]))

(def ^:private err-types {:domains "Domain"
                          :accountabilities "Accountability"})

(defn- validate-things
  "Abstract function to validate collections of things in a circle. Checks that
   the role name is non-empty and present. Also calls the custom validation
   function.

   If the check-fn returns true, throws an error with the given
   err-msg-fmt. err-msg-fmt be in the form of ^.*%s.*%s.*%s.*$ where the
   first %s is to be replaced with a singular English representation of
   the given type (see err-types), the second %s is to be replaced with the
   thing itself and the third with the role-name.

   For example: %s '%s' already exists on role '%s'"
  [circle role-name type thing check-fn err-msg-fmt]
  (validate-role-updates circle role-name)
  (validate-not (check-fn (get-things circle role-name type) thing)
                (format err-msg-fmt (err-types type) thing role-name)))

(defn- add-to
  "Abstract function that adds anything to a set of things in a role in a
   circle. Performs all validation and so forth. Creates the set if it doesn't
   exist."
  [circle role-name type thing]
  (validate-things circle role-name type thing contains?
                   "%s '%s' already exists on role '%s'")
  (let [things (get-things circle role-name type)
        circle (if things circle
                 (update-in circle [:roles role-name] assoc type #{}))]
    (update-in circle [:roles role-name type] conj thing)))

(defn- remove-from
  "Abstract function that removes a thing from a set of things in a role in a
   circle. Performs all validation and so forth. Removes the set if it's empty."
  [circle role-name type thing]
  (validate-things circle role-name type thing (comp not contains?)
                   "%s '%s' doesn't exist on role '%s'")
  (let [result (update-in circle [:roles role-name type] disj thing)]
    (if (empty? (get-things result role-name type))
      (update-in result [:roles role-name] dissoc type) result)))

;; These functions are critical to maintaining namespace encapsulation. Simply
;; allowing an external actor to call directly into the "add-to" and
;; "remove-from" functions artificially constrains this namespace from
;; easily being able to cause these functions to differentiate themselves from
;; one another should they need to, adding a heavier burden on future
;; maintainers.
(defn add-domain
  "Add a domain to a role in the given circle."
  [circle role-name domain]
  (add-to circle role-name :domains domain))

(defn remove-domain
  "Remove a domain from a role in the given circle."
  [circle role-name domain]
  (remove-from circle role-name :domains domain))

(defn add-accountability
  "Add an accountability to a role in the given circle."
  [circle role-name accountability]
  (add-to circle role-name :accountabilities accountability))

(defn remove-accountability
  "Remove an accountability from a role in the given circle."
  [circle role-name accountability]
  (remove-from circle role-name :accountabilities accountability))