# ZIO Event Sourcing

ZIO EventSourcing is library trying to adopt one of CQRS implementations to ZIO way

## Concepts

- Everything happening to system is "event"
- Every "interesting" event is recorded to `EventJournal`
- Every successful persisted event changes calculated state of `Aggregate`     
- Every state could be reassembled by playback of all eventgits to specific `Aggregate` (called `Aggregate`)
- Any of `EventJournal`s could be assembled to multiple `Aggregates`