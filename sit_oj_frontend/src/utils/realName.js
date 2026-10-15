export const isValidRealName = (value) =>
  typeof value === 'string' && value.trim().length > 0 &&
  Array.from(value).length <= 50
