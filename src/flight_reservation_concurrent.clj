(ns flight-reservation_concurrent
  (:require [clojure.string]
            [clojure.pprint]
            [input-sequential-vs-parallel :as input])
    ;[input-random :as input])
  )


;{} hash-map
;[] vectors
(defn initialize-flights [input-flights]
  (map (fn [flight] {:id      (get flight :id),
                     :from    (get flight :from),
                     :to      (get flight :to),
                     :carrier (get flight :carrier),
                     :pricing (map (fn [price] (ref price)) (get flight :pricing)),
                     :status  (ref true)}) input-flights))

(defn initialize-customers [input-customers]
  (map (fn [customer] (agent customer)) input-customers)
  ;{:id  0 :from "BRU" :to "ATL" :seats 5 :budget 700}
  )

(defn flight-has-seats? [seats-quantity budget flight-seats]
  (if (> (count (filter (fn [x]
                          (and (>= budget (nth @x 0))
                               ;available seats
                               (<= seats-quantity (- (nth @x 1) (nth @x 2))))) flight-seats)) 0)
    true false)
  )

(defn bookable-flight? [to from required-seats budget flight]
  (if (and (= @(get flight :status) true)
           (= (get flight :to) to)
           (= (get flight :from) from)
           (flight-has-seats? required-seats budget (get flight :pricing))
           ) true false)
  )

(defn find-flight [to from seats budget flights]
  (let [final-flight (atom nil)]
    ;(doseq [flight flights]
    ;  (if (bookable-flight? to from seats budget flight)
    ;    ;here we can implement so that we find the cheapest flight
    ;    (swap! final-flight (fn [x] flight))
    ;    ))
    (rand-nth (filter (fn [flight] (bookable-flight? to from seats budget flight)) flights))
    ;@final-flight)
    ))

(defn book-seats [available-seats quantity-needed budget]
  (let [possible-seats (filter (fn [seat] (if (and (<= (nth @seat 0) budget) (>= (- (nth @seat 1) (nth @seat 2)) quantity-needed))
                                            true
                                            false)) available-seats)]

    (if (not= (first possible-seats) nil)
      (do
        (ref-set (first possible-seats)
                 [(nth @(first possible-seats) 0) (nth @(first possible-seats) 1) (+ (nth @(first possible-seats) 2) quantity-needed)])
        ;(println "seat booked")
        )
      )))

(defn fire-customers [customers function]
  (doseq [customer customers]
    (send customer function)
    )
  )

(defn print-flights [flights]

  (doseq [flight flights]
    (println "//////////////////////")
    (println "Flight with id: ")
    (println (get flight :id))
    (println "from: ")
    (println (get flight :from))
    (println "to: ")
    (println (get flight :to))
    (println "carrier: ")
    (println (get flight :carrier))
    (println "seats: ")
    (doseq [seats (get flight :pricing)]
      (println @seats)
      )

    )
  )

;MUST BE CALLED IN A DOSYNC
(defn disable-flights [carrier flights value]
  ;first disable those flights
  (map (fn [flight] (if (= (carrier) @(get flight :carrier))
                      (ref-set (get flight :status) false)))))

;:pricing [[600 150 0] ; price; # seats available at that price; # seats taken at that price
;           [650  50 0]
;           [700  50 0]
;           [800  50 0]]}
;MUST BE CALLED IN A DOSYNC
(defn update-pricing [pricing percentage]
  (let [price (nth @pricing 0)
        available-seats (nth @pricing 1)
        taken-seats (nth @pricing 2)]
    (ref-set pricing [(* price percentage) available-seats taken-seats])
    )
  )

(defn apply-sale [carrier flights]
  (map (fn [flight] (if (= (carrier) @(get flight :carrier))
                      (do
                        (map (fn [price] (update-pricing price 0.8)) (get flight :pricing))
                        (ref-set (get flight :status) true)))) flights))




(defn end-sale [carrier flights]
  "End sale: all flights of `carrier` +25% (inverse of -20%)."
  (map (fn [flight] (if (= (carrier) @(get flight :carrier))
                      (do
                        (map (fn [price] (update-pricing price 1.25)) (get flight :pricing))
                        (ref-set (get flight :status) true)))) flights)
  )

(defn over-booked-flight? [flight]
  (let [over-booked-pricings
        (filter (fn [pricing] (if (< (nth @pricing 1) (nth @pricing 2))
                                                     true
                                                     false)) (get flight :pricing))]
    (if (> (count over-booked-pricings) 0)
      true))
  )

(defn verify-overbooking [flights]
  (if (> (count (filter over-booked-flight? flights)) 0)
    true))


;init definitions
(def flights (initialize-flights input/random-flights))
(def customers (initialize-customers input/customers))
(def pending-customers (ref (count customers)))
(def carriers input/carriers)
(def time-between-sales input/TIME_BETWEEN_SALES)
(def sale-time input/TIME_OF_SALES)
(def sale-in-progress (ref false))
(def sales-agent (agent 0))
(def erroneous-sales (atom 0))
(def reservation-retries (atom 0))

;this function assumes all flights belong to the same carrier and the normal price of a seat to be 10
;this method assumes it is run in a dosync block
(defn verify-carrier-sale [flights]

  (let [flights-pricings (map (fn [flight] (get flight :pricing)) flights)]
    ;verify each pricing for the price based on the state of sale-in-progress
    (let [price (atom -1)]
      (doseq [flight-pricings flights-pricings]
        (doseq [pricing flight-pricings]
          (if (= @price -1)
              (compare-and-set! price -1 (nth @pricing 0)))
          (if (not= (nth @pricing 0) @price)
            (do
              (println "problem with prices")
              (swap! erroneous-sales inc))))))))

;(defn reserve-flight [info]
;
;  (dosync (let [to (get info :to)
;                from (get info :from)
;                seats (get info :seats)
;                budget (get info :budget)
;                ;flight (find-flight to from seats budget flights)
;                flight (find-flight2 flights info)]
;            ;(swap! sales-attempts inc)
;            ;(verify-carrier-sale flights)
;
;            (if (not= flight nil)
;              (do
;                (book-seats (get (get flight :flight) :pricing) seats budget)
;                  )
;              )
;            (commute pending-customers dec))))

;previous version
(defn reserve-flight [info]

  (dosync (let [to (get info :to)
                from (get info :from)
                seats (get info :seats)
                budget (get info :budget)
                flight (find-flight to from seats budget flights)
                ]
            ;(swap! sales-attempts inc)
            ;(verify-carrier-sale flights)
            (swap! reservation-retries inc)
            (if (not= flight nil)
              (do
                (book-seats (get flight :pricing) seats budget)
                )
              )
            (commute pending-customers dec))))

(defn do-sale [state]
  (while (not= @pending-customers 0)
    ;select a random carrier
    (let [carrier (rand-nth carriers)]
      ;(println "initiating a sales process")
      (dosync
        (apply-sale carrier flights)
        (ref-set sale-in-progress true))
      (Thread/sleep sale-time)
      ;sale's over restore prices
      (dosync
        (end-sale carrier flights)
        (ref-set sale-in-progress false))
      ;(println "Sale finished")
      (Thread/sleep time-between-sales)))
  false)
(defn main []
  ;for each flight obtain the pricing refs
  ;get the prices out of every ref
  ;do a set of it and see if there's more than 1 unique value
  ;
  ;(println "Number of flights:")
  ;(println (count flights))
  ;(println "Number of customers:")
  ;(println (count customers))

  (send sales-agent do-sale)
  (fire-customers customers reserve-flight)

  ;(println (time (find-flight2 flights (second input/customers))))
  ;(println (time (find-flight  "BOG" "BRU" 2 615 flights)))
  ;await customers to finish
  (while (not= @pending-customers 0)
    (do
      )

    )
  ;(println "all agents finished")
  ;(if (verify-overbooking flights)
  ;  (println "Flights have been overbooked.")
  ;  (println "No overbooking detected."))
  ;(if (> @erroneous-sales 0)
  ;  (do (println "Inconsistency amongs prices found.")
  ;      (println @erroneous-sales))
  ;  (println "No inconsistencies in sales prices"))
  ;(println "flights attempts")
  ;(println @sales-attempts)
  ;(print-flights flights)
  )

(println (time (main)))
(println @reservation-retries)
;(print-flights flights)
(shutdown-agents)
