import { createReviewController } from "./reviewControllerFactory.js";
const controller = createReviewController({
  reviewTable:"trip_reviews", resourceColumn:"trip_id",
  resourceTable:"trips", routeParam:"tripId"
});
export const {createReview,editReview,deleteReview}=controller;
export const listReviewsForTrip=controller.listReviews;
