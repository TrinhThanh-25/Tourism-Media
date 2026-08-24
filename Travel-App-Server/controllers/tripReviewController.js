import { createReviewController } from "./reviewControllerFactory.js";

const isOwner = (trip, user) => Boolean(user) && trip.user_id === user.id;

const controller = createReviewController({
  reviewTable:"trip_reviews", resourceColumn:"trip_id",
  resourceTable:"trips", routeParam:"tripId",
  resourceSelect:"id,user_id,is_post",
  // Reviews of a private trip stay between the trip and its owner.
  canRead:(trip,user) => Number(trip.is_post) === 1 || isOwner(trip,user)
    ? null : {status:403,message:"This trip is not published"},
  canWrite:(trip,user) => Number(trip.is_post) === 1
    ? null : {status:403,message:"Only published trips can be reviewed"}
});

export const {createReview,editReview,deleteReview}=controller;
export const listReviewsForTrip=controller.listReviews;
