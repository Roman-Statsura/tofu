package tofu.internal.instances

import cats.tagless.{ContravariantK, FunctorK}
import cats.{Functor, ~>}
import cats.data.ReaderT
import tofu.PerformOf.Cont
import tofu.syntax.funk._
import tofu.syntax.monadic._
import tofu.{PerformOf, PerformVia, Performer}
import cats.data.Kleisli

final class PerformerContravariantK[F[_], Cancel] extends ContravariantK[Performer[F, *[_], Cancel]] {
  def contramapK[C1[_], C2[_]](af: Performer[F, C1, Cancel])(fk: C2 ~> C1): Performer[F, C2, Cancel] =
    new Performer[F, C2, Cancel] {
      def perform[A](cont: C2[A])(f: F[A]): F[Cancel] = af.perform(fk(cont))(f)
    }
}

final class PerformViaContravariantK[F[_], Cancel] extends ContravariantK[PerformVia[F, *[_], Cancel]] {
  def contramapK[C1[_], C2[_]](af: PerformVia[F, C1, Cancel])(fk: C2 ~> C1) =
    new PerformViaMappedPerformer(af, fk)
}

class PerformViaMappedPerformer[F[_], C1[_], C2[_], Cancel](
    af: PerformVia[F, C1, Cancel],
    fk: C2 ~> C1,
) extends PerformVia[F, C2, Cancel] {
  private[this] val pcontra = Performer.contravariantK[F, Cancel]

  def performer: F[Performer[F, C2, Cancel]] = af.performer.map(pcontra.contramapK(_)(fk))
  implicit def functor: Functor[F]           = af.functor
}

class PerformOfMappedPerformer[F[_], Ex1[_], Ex2[_]](
    af: PerformOf[F, Ex1],
    fk: Ex1 ~> Ex2,
) extends PerformViaMappedPerformer[F, Cont[Ex1, *], Cont[Ex2, *], Unit](
      af,
      funK[Cont[Ex2, *], Cont[Ex1, *]](c1 => ex1 => c1(fk(ex1))),
    ) with PerformOf[F, Ex2]

final class PerformOfFunctorK[F[_]] extends FunctorK[PerformOf[F, *[_]]] {
  def mapK[Ex1[_], Ex2[_]](af: PerformOf[F, Ex1])(fk: Ex1 ~> Ex2): PerformOf[F, Ex2] =
    new PerformOfMappedPerformer(af, fk)
}

final class ReaderTPerformer[F[_], R, C[_], Cancel](p: Performer[F, C, Cancel], r: R)
    extends Performer[ReaderT[F, R, *], C, Cancel] {
  def perform[A](cont: C[A])(f: Kleisli[F, R, A]): Kleisli[F, R, Cancel] =
    ReaderT.liftF(p.perform(cont)(f.run(r)))
}

final class PerformViaReader[F[_]: Functor, R, C[_], Cancel](
    p: PerformVia[F, C, Cancel]
) extends PerformVia[ReaderT[F, R, *], C, Cancel] {
  val functor: Functor[ReaderT[F, R, *]] = implicitly

  def performer: ReaderT[F, R, Performer[ReaderT[F, R, *], C, Cancel]] =
    ReaderT(r => p.performer.map(new ReaderTPerformer(_, r)))
}