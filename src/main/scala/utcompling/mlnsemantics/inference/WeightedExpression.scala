package utcompling.mlnsemantics.inference

class WeightedExpression[T] {

}

case class SoftWeightedExpression[T](expression: T, weight: Double) extends WeightedExpression[T] {

}

case class HardWeightedExpression[T](expression: T) extends WeightedExpression[T] {

}
