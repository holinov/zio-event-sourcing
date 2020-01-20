package zio.es

import zio.Task

case class AggregateBehaviour[E, S](initialState: S, aggregations: (S, E) => Task[S])

object AggregateBehaviour {
  final case class PartialBehaviourBuilder[S](initialState: S) {
    def aggregate[E](aggregations: (S, E) => Task[S]): AggregateBehaviour[E, S] =
      new AggregateBehaviour(initialState, aggregations) //TODO make non effectfull alternatives
  }

  def from[S](initialState: S): PartialBehaviourBuilder[S] = PartialBehaviourBuilder(initialState)
}
