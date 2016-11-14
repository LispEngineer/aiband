;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Aiband item management

;; Entities are maps just as usual. However, they all have an
;; id parameter and each item has a unique and stable ID. 
;; This can be used to track it around.
;;
;; To allow entities to be used for almost any purpose (e.g.,
;; monsters) each item also has a :type.
;;
;; Entity locations are not part of the item themselves.
;; Entities are stored in a map indexed by coordinates
;; [x y] if they are on the map, or are held in the parent
;; object/item if they are contained within something else.
;; Hence, a chest might be in an [x y] coordinate, while the
;; items in the chest will be within that map.
;;
;; The location map contains a vector of all the items within
;; it. This could be a set, but we'll use a vector for now.

(ns aiband.item
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.clrjvm :refer :all :reload true]))

(def last-entity-id
  "Atom holding the last assigned entity ID."
  (atom 1000N))

(defn get-entity-id
  "Gets the next unique Entity ID."
  []
  (dosync
    (swap! last-entity-id inc)))

(defn create-entity
  "Creates an entity by assigning it a (unique) ID and the type.
   This is simply a map with :id and :type fields. All other relevant
   information about the item can be assoc'd in later."
  [entity-type]
  {:id (get-entity-id) :type entity-type})

;; Entity location map:
;; {[x y] [entities...]}

(defn conjv
  "Like standard conj, but if the first arg is nil, returns a
   vector instead of list."
  [coll item]
  ((fnil conj []) coll item))

(defn create-entity-location-map
  "Creates an entity-location-map. (Just an empty hash.)"
  []
  {})

(defn add-entity
  "Adds an entity to an entity/location map. This assumes the entity is new
   and does not check if it's a duplicate (by entity ID).
   elm - entity location map to update;
   coord - [x y] where to add the item;
   entity - item to add."
  [elm coord entity]
  (assoc elm coord (conjv (get elm coord) entity)))

(defn find-entity
  "Finds an entity by ID in the entity-location-map. Returns
   nil if not found, or a vector [[x y] {entity}] if it is found.
   You can pass in a number (which will be searched for the ID) or
   an entity which will have its ID matched."
  [elm entity-id]
  (let [id (cond
             (number? entity-id) entity-id
             :else (:id entity-id))]
    ;; Take the first coordinate that has any entities with the ID
    (first
      ;; Filter out any coords without any found entities
      (filter
        (fn [[coord entities]] entities)
        ;; Find all coords that have an item with this id
        (map (fn [[coord entities]]
                [coord (first (filter #(= id (:id %)) entities))])
             elm)))))

(defn remove-entity
  "Removes all entities from the entity-location-map that have the
   specified ID. Returns the new entity-location-map. This may lead
   to some coordinates in the map having no entities; those are NOT
   removed from the map currently.
   You can pass in a number (which will be searched for the ID) or
   an entity which will have its ID matched."
  [elm entity-id]
  (let [id (cond
             (number? entity-id) entity-id
             :else (:id entity-id))]
    (if (nil? (find-entity elm id))
      ;; If it's not in the elm, return the elm unchanged.
      ;; This is to prevent unnecessary changes to the map.
      ;; Not very efficient, but oh well.
      elm
      ;; Otherwise, go through the full map and remove any
      ;; mentions of this ID.
      (into {}
        (map (fn [[coord entities]]
                [coord (filterv #(not= id (:id %)) entities)])
             elm)))))

(defn update-entity
  "Removes any other copies of this entity from this entity-location-map
   and adds it back at the specified location."
   ;; TODO: If location is null, updates the item in place?
  [elm coord entity]
  (add-entity (remove-entity elm entity) coord entity))

(defn all-entities
  "Returns all entities as a sequence of [[x y] {entity}] forms."
  [elm]
  (apply concat
    (map 
      (fn [[coord entities]]
        (map (fn [entity] [coord entity]) entities))
      elm)))

;; TODO:
;; 1. Function that updates an entity in the elm "in place" with an updater function?

;; ITEMS ----------------------------------------------------------------------

(defn create-item
  "Creates a new item with the specified :item-type."
  [item-type]
  (assoc (create-entity :item) :item-type item-type))

(defn all-items
  "Returns all items as a sequence of [[x y] {item-entity}] forms."
  [elm]
  (filter
    (fn [[coord entity]] (= (:type entity) :item))
    (all-entities elm)))