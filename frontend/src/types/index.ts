// Source types
export type SourceType = 'RSS' | 'EMAIL' | 'WEB' | 'REDDIT';
export type SourceStatus = 'ACTIVE' | 'PAUSED' | 'ERROR' | 'DELETED';

// Brief types
export type BriefingFrequency = 'DAILY' | 'WEEKLY';
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type DayBriefStatus = 'ACTIVE' | 'PAUSED' | 'DELETED';

// Report types
export type ReportStatus = 'PENDING' | 'GENERATED' | 'ERROR';

// News item types
export type NewsItemStatus = 'PENDING' | 'SUMMARIZED' | 'PROCESSED' | 'ERROR';

export interface NewsItemTags {
  topics?: string[];
  people?: string[];
  companies?: string[];
  technologies?: string[];
  sentiment?: string;
}

export interface NewsItemDTO {
  id: string;
  title: string;
  link: string;
  author: string | null;
  publishedAt: string | null;
  content: string | null;
  summary: string | null;
  tags: NewsItemTags | null;
  sourceId: string;
  sourceName: string;
  readAt: string | null;
  saved: boolean;
  score: number | null;
  scoreReasoning: string | null;
  createdAt: string;
}

export type ReadFilter = 'ALL' | 'UNREAD' | 'READ';

// Source DTO
export interface SourceDTO {
  id: string;
  briefingId: string;
  briefingTitle: string;
  url: string;
  extractionPrompt: string | null;
  name: string;
  emailAddress: string | null;
  type: SourceType;
  status: SourceStatus;
  tags: string[];
  lastFetchedAt: string | null;
  lastError: string | null;
  itemCount: number;
  unreadCount: number;
  createdAt: string;
}

// DayBrief DTO
export interface DayBriefDTO {
  id: string;
  title: string;
  description: string | null;
  briefing: string;
  frequency: BriefingFrequency;
  scheduleDayOfWeek: DayOfWeek | null;
  scheduleTime: string; // HH:mm:ss
  timezone: string;
  status: DayBriefStatus;
  lastExecutedAt: string | null;
  sourceCount: number;
  createdAt: string;
  emailDeliveryEnabled: boolean;
  position: number;
}

// Report Item DTO
export interface ReportItemDTO {
  id: string;
  newsItemId: string;
  title: string;
  link: string;
  sourceName: string;
  summary: string | null;
  score: number;
  position: number;
}

// Daily Report DTO
export interface DailyReportDTO {
  id: string;
  dayBriefId: string;
  dayBriefTitle: string;
  dayBriefDescription: string | null;
  generatedAt: string;
  status: ReportStatus;
  items: ReportItemDTO[];
  itemCount: number;
}

// Request types
export interface CreateSourceRequest {
  briefingId: string;
  url?: string;
  name: string;
  type: SourceType;
  tags?: string[];
  extractionPrompt?: string;
}

export interface UpdateSourceRequest {
  name?: string;
  tags?: string[];
  status?: 'ACTIVE' | 'PAUSED';
  extractionPrompt?: string;
}

export interface CreateDayBriefRequest {
  title: string;
  description?: string;
  briefing: string;
  frequency: BriefingFrequency;
  scheduleDayOfWeek?: DayOfWeek;
  scheduleTime: string;
  timezone?: string;
  emailDeliveryEnabled?: boolean;
}

export interface UpdateDayBriefRequest {
  title?: string;
  description?: string;
  briefing?: string;
  sourceIds?: string[];
  frequency?: BriefingFrequency;
  scheduleDayOfWeek?: DayOfWeek;
  scheduleTime?: string;
  timezone?: string;
  status?: 'ACTIVE' | 'PAUSED';
  emailDeliveryEnabled?: boolean;
}

