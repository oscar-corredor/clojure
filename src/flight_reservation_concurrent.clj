(ns flight-reservation_concurrent
  (:require [clojure.string]
            [clojure.pprint]
            [input-random :as input]
    #_[input-random :as input])
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
    (doseq [flight flights]
      (if (bookable-flight? to from seats budget flight)
        ;here we can implement so that we find the cheapest flight
        (swap! final-flight (fn [x] flight))
        ))
    @final-flight))

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
      ;(println "no seat found for customer")
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


(defn main []
  (println "Initializing.")
  (def flights (initialize-flights input/flights))
  (def customers (initialize-customers input/customers))
  (def pending-customers (atom (count customers)))
  (def carriers input/carriers)
  (def time-between-sales input/TIME_BETWEEN_SALES)
  (def sale-time input/TIME_OF_SALES)
  (def sales-agent (agent 0))
  ;(def sales-manager (agent ))

  (defn reserve-flight [info]
    (dosync (let [to (get info :to)
                  from (get info :from)
                  seats (get info :seats)
                  budget (get info :budget)
                  flight (find-flight to from seats budget flights)]
              (if (not= flight nil)
                (book-seats (get flight :pricing) seats budget)
                ;(println "no flight found for customer")
                )
              ;COMMUTE THIS SHIT
              (swap! pending-customers dec))))


  (defn do-sale [state]
    (while (and (not= @pending-customers 0) state)
      ;select a random carrier
      (let [carrier (rand-nth carriers)]
        (println "initiating a sales process")

        (dosync
          (apply-sale carrier flights))
        (Thread/sleep sale-time)
        ;sale's over restore prices
        (dosync
          (end-sale carrier flights))
        (println "Sale finished")
        (Thread/sleep time-between-sales)))
    false)
  (println "initiating sales agent")
  (send sales-agent do-sale)
  (println "customers to be attended")
  (println @pending-customers)
  (fire-customers customers reserve-flight)
  ;await customers to finish
  (while (not= @pending-customers 0)
    (do)

    )
  (println "all agents finished")
  ;(doseq [customer input/customers]
  ;  ;(println "The flight for customer: ")
  ;  ;(println customer)
  ;  ;(println "////////")
  ;  ;(println (find-flight (get customer :to) (get customer :from) (get customer :seats) (get customer :budget) flights))
  ;
  ;  (reserve-flight customer)
  ;
  ;  )
  ;(println "finished printing")
  (print-flights flights)
  ;(println "////////")
  ;(println "Done.")

  )

(main)
