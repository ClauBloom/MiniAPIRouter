import type { ApiProblem } from './contracts'

export class ApiError extends Error {
  readonly errorCode: string
  readonly traceId: string | undefined
  readonly fieldErrors: Map<string, string>
  constructor(problem: ApiProblem) {
    super(problem.message)
    this.name = 'ApiError'
    this.errorCode = problem.error_code
    this.traceId = problem.trace_id
    this.fieldErrors = new Map((problem.details ?? []).map(item => [item.field, item.reason]))
  }
}
