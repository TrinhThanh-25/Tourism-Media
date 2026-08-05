import Joi from 'joi';

// returns express middleware that validates req[source] with provided Joi schema
export function validateSchema(schema, source = 'body') {
  return (req, res, next) => {
    const data = req[source] || {};
    const { error, value } = schema.validate(data, { abortEarly: false, stripUnknown: true, convert: true });
    if (error) {
      return res.status(400).json({ error: 'Validation failed', details: error.details.map(d => d.message) });
    }
    // Express 5 exposes req.query through a getter, so redefine it with the
    // converted Joi value instead of assigning to the read-only property.
    if (source === 'query') {
      Object.defineProperty(req, 'query', { value, configurable: true, enumerable: true });
    } else {
      req[source] = value;
    }
    next();
  };
}

export default validateSchema;
