import { LessonPlayer } from "@/components/school/Learn";

export default async function Page({ params }: { params: Promise<{ course: string; lesson: string }> }) {
  const { course, lesson } = await params;
  return <LessonPlayer courseId={course} lessonId={lesson} />;
}
