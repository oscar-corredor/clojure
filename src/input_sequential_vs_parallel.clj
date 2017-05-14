(ns input-sequential-vs-parallel)
(def origins ["LON", "BRU","LAX","NY"])
(def destinations ["BOG", "SP","CMX","PTY"])
(def carriers ["AVIANCA", "LUFTHANSA", "LATAM", "TACA", "AA"])

(def random-flights
  (for [id (range 500)
        :let [from (rand-nth origins)
              to (rand-nth destinations)]]
    {:id     id
     :from   from
     :to     to
     :carrier  (rand-nth carriers)
     :pricing [[(+ 50 (rand-int 100)) 150 0]
               [(+ 100 (rand-int 100)) 150 0]
               [(+ 200 (rand-int 100)) 150 0]]}))

(def customers
  (for [id (range 1000)
        :let [{from :from to :to} (rand-nth random-flights)]]
    {:id     id
     :from   from
     :to     to
     :seats  (+ (rand-int 4) 1)        ; 1-4
     :budget (+ (rand-int 600) 200)})) ; 200-799




;(def carriers (distinct (map :carrier random-flights)))

(def TIME_BETWEEN_SALES 10) ; milliseconds
(def TIME_OF_SALES 10)
