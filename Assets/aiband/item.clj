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
    (swap! last-item-id inc)))

(defn create-entity
  "Creates an entity by assigning it a (unique) ID and the type.
   This is simply a map with :id and :type fields. All other relevant
   information about the item can be assoc'd in later."
  [entity-type]
  {:id (get-item-id) :type entity-type})

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



;; ITEMS ----------------------------------------------------------------------

(defn create-item
  "Creates a new item with the specified type."
  [item-type]
  (assoc (create-entity :item) :item-type item-type))

