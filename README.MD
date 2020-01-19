# ZIO Event Sourcing
ZIO EventSourcing is library trying to adopt one of CQRS implementations to ZIO way.

## Description
Most of application could be seen as sequence of user initiated events that change state of the entire system. 
Keeping this in mind we could say that all entire system state could be restored just by playback of whole history of user initiated events, or some times just a part of them to business-logic processors (if they are "state less").
But also we need to keep in mind that modern time our services need to be fault-tolerant, scalable and able to adopt to always changing requirements of Clients and Business.
And also we have requirement to provide our marketing team tools to allow to sell our product and keep KPI progress on it.  

Combining previous requirements we have that modern system:  
- Should not lose any of "user initiated data" 
- Should be able to recover from most possible ways corrupting "customer data"
- Should have natural ability to scale on either "user input/output" or marketing analytics data

Event sourcing addresses this issues by separating "read side" and "write side"
It has "event journal" which stores incoming events ordered in time for each entity ("Aggregate"). When entity state needs to be accessed "Aggregate" loads stored events and computes totals state 
 
### Concepts
- Everything happening to system is "event"
- Every "interesting" event is recorded to `EventJournal`
- Every successful persisted event changes calculated state of `Aggregate`     
- Every state could be reassembled by playback of all eventgits to specific `Aggregate`
- Any of `EventJournal`s could be assembled to multiple `Aggregates`

### Benefits
- Separating read and write sides eliminates all possibilities of "race conditions" in concurrent environments (all changes are being processed on stable order)
- Ability to recover from "cold data"
- Ability to add new type of business or analytics aggregates at any time
- Possibility to take most advantages from Cassandra\Scylla\Aerospike storages
   
### Drawbacks
- To get current state of aggregate you need to read all its data (addressed at "read side" by  snapshot storage or other caching techniques)
- In real life you will need to have some "distributed tracing" support to debug issues
- Having some problems doing distributed transactions (addressed by [zio-saga](https://github.com/VladKopanev/zio-saga))

## TO DO
- [ ] Publish to JCenter/Maven Central 
- [ ] Optional state snapshot storage
- [ ] Storage examples for Cassandra/Scylla and [zio-rocksdb](https://github.com/zio/zio-rocksdb)
- [ ] [zio-saga](https://github.com/VladKopanev/zio-saga) example
- [ ] [ZIO Kafka](https://github.com/zio/zio-kafka) example
