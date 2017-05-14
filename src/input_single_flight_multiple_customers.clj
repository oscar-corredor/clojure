(ns input-single-flight-multiple-customers)

(def flights
  [
   {:id 0
    :from "BRU" :to "LON"
    :carrier "Ryanair"
    :pricing [[100 50000 0] ; price; # seats available at that price; # seats taken at that price
              ]}
   ])

(def customers
  (for [id (range 100000)
        :let [{from :from to :to} (rand-nth flights)]]
    {:id     id
     :from   from
     :to     to
     ;:seats  (+ (rand-int 4) 1)        ; 1-4
     :seats  1        ; 1-4
     :budget (+ (rand-int 600) 200)})) ; 200-799

(def carriers (distinct (map :carrier flights)))

(def TIME_BETWEEN_SALES 50) ; milliseconds
(def TIME_OF_SALES 10)
