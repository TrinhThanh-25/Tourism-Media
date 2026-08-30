import { createReviewController } from "./reviewControllerFactory.js";
const controller = createReviewController({
  reviewTable:"location_reviews", resourceColumn:"location_id",
  resourceTable:"locations", routeParam:"locationId"
});
export const {createReview,editReview,deleteReview}=controller;
export const listReviewsForLocation=controller.listReviews;
