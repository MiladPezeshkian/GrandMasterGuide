import { CourseView } from "@/components/school/Learn";

export default async function Page({ params }: { params: Promise<{ course: string }> }) {
  const { course } = await params;
  return <CourseView courseId={course} />;
}
