(ns same-seat-pricing)
(def origins ["LON", "BRU","LAX","NY"])
(def destinations ["BOG", "SP","CMX","PTY"])

(def random-flights
  (for [id (range 100)
        :let [from (rand-nth origins)
              to (rand-nth destinations)]]
    {:id     id
     :from   from
     :to     to
     :carrier  "Avianca"
     :pricing [[10 150 0]
               [10 150 0]
               [10 150 0]]}))

(def customers
  (for [id (range 50000)
        :let [{from :from to :to} (rand-nth random-flights)]]
    {:id     id
     :from   from
     :to     to
     :seats  (+ (rand-int 4) 1)        ; 1-4
     :budget (+ (rand-int 600) 200)})) ; 200-799




(def carriers (distinct (map :carrier random-flights)))

(def TIME_BETWEEN_SALES 50) ; milliseconds
(def TIME_OF_SALES 10)
