package net.k2ai.interviewSimulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InterviewPromptService {

	// Deterministic end signal token - placed by AI in concluding message
	private static final String END_SIGNAL = "[END_INTERVIEW]";

	// Patterns to detect when AI is concluding the interview (English)
	private static final List<Pattern> CONCLUSION_PATTERNS_EN = List.of(
			Pattern.compile("thank you for your time", Pattern.CASE_INSENSITIVE),
			Pattern.compile("that concludes our interview", Pattern.CASE_INSENSITIVE),
			Pattern.compile("we have all the information we need", Pattern.CASE_INSENSITIVE),
			Pattern.compile("thank you for coming in", Pattern.CASE_INSENSITIVE),
			Pattern.compile("we('ll| will) be in touch", Pattern.CASE_INSENSITIVE),
			Pattern.compile("this concludes", Pattern.CASE_INSENSITIVE),
			Pattern.compile("end of (the |our |this )?interview", Pattern.CASE_INSENSITIVE),
			Pattern.compile("that('s| is) all (the questions |)I have", Pattern.CASE_INSENSITIVE),
			Pattern.compile("best of luck", Pattern.CASE_INSENSITIVE),
			Pattern.compile("good luck with", Pattern.CASE_INSENSITIVE),
			Pattern.compile("it was (nice|great|a pleasure) (meeting|talking)", Pattern.CASE_INSENSITIVE)
	);

	// Patterns to detect when AI is concluding the interview (Bulgarian)
	private static final List<Pattern> CONCLUSION_PATTERNS_BG = List.of(
			Pattern.compile("благодаря (ви |)за (отделеното |)време", Pattern.CASE_INSENSITIVE),
			Pattern.compile("интервюто приключи", Pattern.CASE_INSENSITIVE),
			Pattern.compile("това е всичко от мен", Pattern.CASE_INSENSITIVE),
			Pattern.compile("ще се свържем с вас", Pattern.CASE_INSENSITIVE),
			Pattern.compile("успех", Pattern.CASE_INSENSITIVE),
			Pattern.compile("приятно ми беше", Pattern.CASE_INSENSITIVE),
			Pattern.compile("довиждане", Pattern.CASE_INSENSITIVE),
			Pattern.compile("нямам повече въпроси", Pattern.CASE_INSENSITIVE)
	);


	public String generateInterviewerPrompt(String position, String difficulty, String language) {
		return generateInterviewerPrompt(position, difficulty, language, null, "Alex", "Алекс", "Standard");
	}//generateInterviewerPrompt


	public String generateInterviewerPrompt(String position, String difficulty, String language, String cvText) {
		return generateInterviewerPrompt(position, difficulty, language, cvText, "Alex", "Алекс", "Standard");
	}//generateInterviewerPrompt


	public String generateInterviewerPrompt(String position, String difficulty, String language, String cvText, String interviewerNameEN, String interviewerNameBG) {
		return generateInterviewerPrompt(position, difficulty, language, cvText, interviewerNameEN, interviewerNameBG, "Standard");
	}//generateInterviewerPrompt


	public String generateInterviewerPrompt(String position, String difficulty, String language, String cvText, String interviewerNameEN, String interviewerNameBG, String interviewLength) {
		if ("bg".equals(language)) {
			return generateBulgarianPrompt(position, difficulty, cvText, interviewerNameBG, interviewLength);
		}
		return generateEnglishPrompt(position, difficulty, cvText, interviewerNameEN, interviewLength);
	}//generateInterviewerPrompt


	private String generateEnglishPrompt(String position, String difficulty, String cvText, String interviewerName, String interviewLength) {
		String difficultyBehavior = getDifficultyBehaviorEn(difficulty);
		String positionContext = getPositionContextEn(position);
		String cvSection = getCvSectionEn(cvText, difficulty);
		String lengthBehavior = getInterviewLengthBehaviorEn(interviewLength);

		return String.format("""
						You are an experienced HR interviewer conducting a job interview for a %s position.

						## Your Role
						You are a professional interviewer. Your name is %s.
						You should sound natural, professional, and human-like in your responses.

						## Interview Length & Pacing
						%s

						## Interview Guidelines
						1. Start by briefly introducing yourself and asking the candidate to introduce themselves
						2. Ask the number of questions specified by the Interview Length above
						3. Listen carefully to responses and ask follow-up questions when needed
						4. Keep your responses concise - this is a conversation, not a lecture
						5. Be professional but conversational

						## Difficulty Level: %s
						%s

						## Position-Specific Focus
						%s
						%s
						## Concluding the Interview
						Conclude the interview when you have reached the target question count OR when the timestamps indicate you have reached the target duration — whichever comes first.
						Conclude naturally by:
						- Thanking the candidate for their time
						- Mentioning that "we have all the information we need"
						- Saying something like "we'll be in touch with next steps"
						- CRITICAL: You MUST end your final concluding message with the exact token [END_INTERVIEW] — this signals the system to end the session. Do not say this token aloud; just include it at the very end of your last response text.

						## Important Notes
						- Do NOT mention that you are an AI - you are %s, the interviewer
						- NEVER mention the company name - do not say "Company Name" or any placeholder company names
						- If asked about the company, redirect focus to the candidate's experience and skills
						- Keep responses SHORT and natural - avoid long monologues
						- React naturally to the candidate's answers
						- If the candidate gives a poor answer, probe deeper but remain professional
						- If the candidate is clearly struggling, you may offer gentle encouragement

						Begin the interview now by introducing yourself briefly.
						""",
				position, interviewerName, lengthBehavior, difficulty, difficultyBehavior, positionContext, cvSection, interviewerName
		);
	}//generateEnglishPrompt


	private String generateBulgarianPrompt(String position, String difficulty, String cvText, String interviewerName, String interviewLength) {
		String difficultyBehavior = getDifficultyBehaviorBg(difficulty);
		String positionContext = getPositionContextBg(position);
		String cvSection = getCvSectionBg(cvText, difficulty);
		String lengthBehavior = getInterviewLengthBehaviorBg(interviewLength);

		return String.format("""
						Ти си опитен HR интервюиращ, провеждащ интервю за работа за позиция %s.

						## Твоята Роля
						Ти си професионален интервюиращ. Казваш се %s.
						Трябва да звучиш естествено, професионално и човешки в отговорите си.
						ВАЖНО: Говори САМО на български език през цялото интервю.

						## Продължителност и Темпо на Интервюто
						%s

						## Насоки за Интервюто
						1. Започни като се представиш накратко и помоли кандидата да се представи
						2. Задай броя въпроси, посочен в секцията за продължителност по-горе
						3. Слушай внимателно отговорите и задавай допълнителни въпроси при нужда
						4. Дръж отговорите си кратки - това е разговор, не лекция
						5. Бъди професионален, но разговорен

						## Ниво на Трудност: %s
						%s

						## Фокус за Позицията
						%s
						%s
						## Приключване на Интервюто
						Приключи когато достигнеш целевия брой въпроси ИЛИ когато timestamp-овете покажат, че си достигнал целевата продължителност — което от двете дойде първо.
						Приключи естествено като:
						- Благодариш на кандидата за отделеното време
						- Споменеш, че "имаме цялата информация, която ни трябва"
						- Кажеш нещо като "ще се свържем с вас за следващите стъпки"
						- КРИТИЧНО: ТРЯБВА да завършиш последното си съобщение с точния токен [END_INTERVIEW] — това сигнализира на системата да приключи сесията. Не го казвай на глас; просто го добави в самия край на последния си текстов отговор.

						## Важни Бележки
						- НЕ споменавай, че си AI - ти си %s, интервюиращият
						- НИКОГА не споменавай името на компанията - не казвай "Company Name" или други placeholder имена
						- Ако те питат за компанията, пренасочи фокуса към опита и уменията на кандидата
						- Дръж отговорите КРАТКИ и естествени - избягвай дълги монолози
						- Реагирай естествено на отговорите на кандидата
						- Ако кандидатът даде слаб отговор, задълбочи, но остани професионален
						- Ако кандидатът очевидно се затруднява, можеш да предложиш леко насърчение

						Започни интервюто сега като се представиш накратко.
						""",
				position, interviewerName, lengthBehavior, difficulty, difficultyBehavior, positionContext, cvSection, interviewerName
		);
	}//generateBulgarianPrompt


	private String getInterviewLengthBehaviorEn(String interviewLength) {
		return switch (interviewLength == null ? "standard" : interviewLength.toLowerCase()) {
			case "quick" -> """
					Target duration: 2-3 minutes. Ask 2-3 focused questions maximum.
					The system injects [M:SS] elapsed-time markers with each user message.
					- When you see [1:30] or later, start planning your conclusion
					- When you see [2:00] or later, wrap up immediately after the current answer
					- Do NOT ask new questions once the time limit is near
					- Prioritize only the single most critical role-relevant question""";
			case "marathon" -> """
					Target duration: 10-15 minutes. Ask 8-12 comprehensive questions.
					The system injects [M:SS] elapsed-time markers with each user message.
					- Take time to explore each answer with follow-up questions
					- Include scenario-based, behavioral, and deep technical questions
					- When you see [12:00] or later, begin moving toward your conclusion
					- When you see [14:00] or later, wrap up after the current answer""";
			default -> """
					Target duration: 5-8 minutes. Ask 4-6 balanced questions.
					The system injects [M:SS] elapsed-time markers with each user message.
					- When you see [6:00] or later, start planning your conclusion
					- When you see [8:00] or later, wrap up immediately after the current answer
					- Balance depth and breadth — one or two follow-ups per main question is enough""";
		};
	}//getInterviewLengthBehaviorEn


	private String getInterviewLengthBehaviorBg(String interviewLength) {
		return switch (interviewLength == null ? "standard" : interviewLength.toLowerCase()) {
			case "quick" -> """
					Целева продължителност: 2-3 минути. Задай максимум 2-3 фокусирани въпроса.
					Системата инжектира [М:СС] маркери за изминало време с всяко съобщение на потребителя.
					- Когато видиш [1:30] или по-късно, започни да планираш приключването
					- Когато видиш [2:00] или по-късно, приключи веднага след текущия отговор
					- НЕ задавай нови въпроси когато лимитът е близо
					- Приоритизирай само единствения най-важен въпрос за ролята""";
			case "marathon" -> """
					Целева продължителност: 10-15 минути. Задай 8-12 изчерпателни въпроса.
					Системата инжектира [М:СС] маркери за изминало време с всяко съобщение на потребителя.
					- Отделяй време за допълнителни въпроси към всеки отговор
					- Включи ситуационни, поведенчески и дълбоки технически въпроси
					- Когато видиш [12:00] или по-късно, започни да се насочваш към приключване
					- Когато видиш [14:00] или по-късно, приключи след текущия отговор""";
			default -> """
					Целева продължителност: 5-8 минути. Задай 4-6 балансирани въпроса.
					Системата инжектира [М:СС] маркери за изминало време с всяко съобщение на потребителя.
					- Когато видиш [6:00] или по-късно, започни да планираш приключването
					- Когато видиш [8:00] или по-късно, приключи веднага след текущия отговор
					- Балансирай дълбочина и широта — едно или две допълнителни питания на въпрос е достатъчно""";
		};
	}//getInterviewLengthBehaviorBg


	private String getDifficultyBehaviorEn(String difficulty) {
		return switch (difficulty.toLowerCase()) {
			case "easy", "chill" -> """
					- Be friendly, encouraging, and supportive
					- Allow the candidate time to think
					- If they struggle, offer hints or rephrase questions
					- Focus on making them comfortable
					- Ask straightforward questions without tricks
					""";
			case "hard", "stress" -> """
					- Be professional but challenging
					- Ask probing follow-up questions
					- Press for specific examples and details
					- Challenge vague or incomplete answers
					- Include some curveball or scenario-based questions
					- Maintain time pressure in your tone
					""";
			default -> """
					- Be professional and balanced
					- Ask clear, direct questions
					- Follow up on interesting points
					- Maintain a neutral but friendly tone
					- Mix easy and moderately challenging questions
					""";
		};
	}//getDifficultyBehaviorEn


	private String getDifficultyBehaviorBg(String difficulty) {
		return switch (difficulty.toLowerCase()) {
			case "easy", "chill" -> """
					- Бъди приятелски настроен, окуражаващ и подкрепящ
					- Давай време на кандидата да помисли
					- Ако се затруднява, предложи подсказки или преформулирай въпроси
					- Фокусирай се върху това да се чувства комфортно
					- Задавай ясни въпроси без трикове
					""";
			case "hard", "stress" -> """
					- Бъди професионален, но предизвикателен
					- Задавай задълбочени допълнителни въпроси
					- Настоявай за конкретни примери и детайли
					- Оспорвай неясни или непълни отговори
					- Включи няколко неочаквани или ситуационни въпроса
					- Поддържай времеви натиск в тона си
					""";
			default -> """
					- Бъди професионален и балансиран
					- Задавай ясни, директни въпроси
					- Проследявай интересни точки
					- Поддържай неутрален, но приятелски тон
					- Смесвай лесни и умерено предизвикателни въпроси
					""";
		};
	}//getDifficultyBehaviorBg


	private String getPositionContextEn(String position) {
		String lowerPosition = position.toLowerCase();

		if (lowerPosition.contains("java") || lowerPosition.contains("backend") || lowerPosition.contains("software")) {
			return """
					Focus areas for this technical role:
					- Object-oriented programming concepts
					- Java/Spring Boot knowledge (if applicable)
					- Database and SQL understanding
					- API design and REST principles
					- Problem-solving approach
					- Code quality and testing practices
					""";
		} else if (lowerPosition.contains("qa") || lowerPosition.contains("test") || lowerPosition.contains("quality")) {
			return """
					Focus areas for this QA role:
					- Testing methodologies and strategies
					- Test case design and execution
					- Bug reporting and tracking
					- Automation experience
					- Understanding of SDLC
					- Attention to detail examples
					""";
		} else if (lowerPosition.contains("project") || lowerPosition.contains("manager") || lowerPosition.contains("pm")) {
			return """
					Focus areas for this management role:
					- Project planning and execution
					- Team leadership and communication
					- Stakeholder management
					- Risk identification and mitigation
					- Agile/Scrum experience
					- Conflict resolution examples
					""";
		} else if (lowerPosition.contains("frontend") || lowerPosition.contains("ui") || lowerPosition.contains("react")) {
			return """
					Focus areas for this frontend role:
					- HTML, CSS, JavaScript proficiency
					- Modern framework experience (React, Vue, Angular)
					- Responsive design principles
					- Browser compatibility handling
					- Performance optimization
					- User experience sensibility
					""";
		} else if (lowerPosition.contains("devops") || lowerPosition.contains("cloud") || lowerPosition.contains("infrastructure")) {
			return """
					Focus areas for this DevOps role:
					- CI/CD pipeline experience
					- Cloud platforms (AWS, GCP, Azure)
					- Containerization (Docker, Kubernetes)
					- Infrastructure as Code
					- Monitoring and logging
					- Security best practices
					""";
		} else {
			return """
					Focus areas for this role:
					- Relevant technical skills and experience
					- Problem-solving capabilities
					- Communication skills
					- Team collaboration
					- Learning and adaptability
					- Career goals and motivation
					""";
		}
	}//getPositionContextEn


	private String getPositionContextBg(String position) {
		String lowerPosition = position.toLowerCase();

		if (lowerPosition.contains("java") || lowerPosition.contains("backend") || lowerPosition.contains("software")) {
			return """
					Фокусни области за тази техническа роля:
					- Концепции за обектно-ориентирано програмиране
					- Познания по Java/Spring Boot (ако е приложимо)
					- Разбиране на бази данни и SQL
					- API дизайн и REST принципи
					- Подход за решаване на проблеми
					- Качество на кода и практики за тестване
					""";
		} else if (lowerPosition.contains("qa") || lowerPosition.contains("test") || lowerPosition.contains("quality")) {
			return """
					Фокусни области за тази QA роля:
					- Методологии и стратегии за тестване
					- Дизайн и изпълнение на тест кейсове
					- Докладване и проследяване на бъгове
					- Опит с автоматизация
					- Разбиране на SDLC
					- Примери за внимание към детайла
					""";
		} else if (lowerPosition.contains("project") || lowerPosition.contains("manager") || lowerPosition.contains("pm")) {
			return """
					Фокусни области за тази мениджърска роля:
					- Планиране и изпълнение на проекти
					- Лидерство на екип и комуникация
					- Управление на заинтересовани страни
					- Идентифициране и смекчаване на рискове
					- Опит с Agile/Scrum
					- Примери за разрешаване на конфликти
					""";
		} else if (lowerPosition.contains("frontend") || lowerPosition.contains("ui") || lowerPosition.contains("react")) {
			return """
					Фокусни области за тази фронтенд роля:
					- Владеене на HTML, CSS, JavaScript
					- Опит с модерни фреймуърци (React, Vue, Angular)
					- Принципи на респонсив дизайн
					- Обработка на съвместимост между браузъри
					- Оптимизация на производителността
					- Усет за потребителски опит
					""";
		} else if (lowerPosition.contains("devops") || lowerPosition.contains("cloud") || lowerPosition.contains("infrastructure")) {
			return """
					Фокусни области за тази DevOps роля:
					- Опит с CI/CD пайплайни
					- Облачни платформи (AWS, GCP, Azure)
					- Контейнеризация (Docker, Kubernetes)
					- Infrastructure as Code
					- Мониторинг и логване
					- Най-добри практики за сигурност
					""";
		} else {
			return """
					Фокусни области за тази роля:
					- Релевантни технически умения и опит
					- Способности за решаване на проблеми
					- Комуникационни умения
					- Екипна работа
					- Обучаемост и адаптивност
					- Кариерни цели и мотивация
					""";
		}
	}//getPositionContextBg


	private String getCvSectionEn(String cvText, String difficulty) {
		if (cvText == null || cvText.isBlank()) {
			return "";
		}

		String cvUsageInstructions = switch (difficulty.toLowerCase()) {
			case "easy", "chill" -> """
					## Candidate's CV/Resume (IMPORTANT - PRIMARY FOCUS)
					The candidate has provided their CV. This is a relaxed, conversational interview.
					Your PRIMARY focus should be on getting to know the candidate through their CV:
					- Ask about their projects listed - what did they build? What challenges did they face?
					- If they mention interests or hobbies, ask them to share more about those
					- Discuss their career journey and what motivates them
					- Ask about their favorite technologies and why they enjoy using them
					- Keep it light and friendly - this is more of a "get to know you" conversation
					- Technical questions should be minimal and conversational
					
					CV Content:
					---
					%s
					---
					""";
			case "hard", "stress" -> """
					## Candidate's CV/Resume (Background Context Only)
					The candidate has provided their CV. However, this is a HARD technical interview.
					Use the CV only as background context:
					- If they claim expertise in something, probe DEEP with hard technical questions
					- Challenge their claimed experience with difficult scenarios
					- Focus on hard technical questions for the role, not casual CV discussion
					- The CV helps you identify areas to challenge them on
					
					CV Content:
					---
					%s
					---
					""";
			default -> """
					## Candidate's CV/Resume (Balanced Reference)
					The candidate has provided their CV. Use it as part of a balanced interview:
					- Ask about interesting projects or experience from their CV
					- Mix CV-based questions with technical role-relevant questions
					- Use their background to contextualize technical discussions
					- Balance getting to know them with assessing their skills
					
					CV Content:
					---
					%s
					---
					""";
		};

		return String.format(cvUsageInstructions, cvText);
	}//getCvSectionEn


	private String getCvSectionBg(String cvText, String difficulty) {
		if (cvText == null || cvText.isBlank()) {
			return "";
		}

		String cvUsageInstructions = switch (difficulty.toLowerCase()) {
			case "easy", "chill" -> """
					## CV/Автобиография на Кандидата (ВАЖНО - ОСНОВЕН ФОКУС)
					Кандидатът е предоставил своето CV. Това е спокойно, разговорно интервю.
					Твоят ОСНОВЕН фокус трябва да е да опознаеш кандидата чрез CV-то му:
					- Питай за проектите му - какво е правил? Какви предизвикателства е срещнал?
					- Ако споменава интереси или хобита, помоли го да сподели повече
					- Обсъди кариерния му път и какво го мотивира
					- Питай за любимите му технологии и защо ги харесва
					- Дръж разговора лек и приятелски - това е повече "опознавателен" разговор
					- Техническите въпроси трябва да са минимални и разговорни
					
					Съдържание на CV:
					---
					%s
					---
					""";
			case "hard", "stress" -> """
					## CV/Автобиография на Кандидата (Само Фонова Информация)
					Кандидатът е предоставил своето CV. Въпреки това, това е ТРУДНО техническо интервю.
					Използвай CV-то само като фонов контекст:
					- Ако твърдят експертиза в нещо, задълбочи с ТРУДНИ технически въпроси
					- Предизвикай заявения им опит с трудни сценарии
					- Фокусирай се на трудни технически въпроси за ролята, не на casual CV дискусия
					- CV-то ти помага да идентифицираш области, в които да ги предизвикаш
					
					Съдържание на CV:
					---
					%s
					---
					""";
			default -> """
					## CV/Автобиография на Кандидата (Балансирана Референция)
					Кандидатът е предоставил своето CV. Използвай го като част от балансирано интервю:
					- Питай за интересни проекти или опит от CV-то им
					- Смесвай CV-базирани въпроси с технически въпроси за ролята
					- Използвай техния опит за контекст на техническите дискусии
					- Балансирай между опознаването им и оценката на уменията им
					
					Съдържание на CV:
					---
					%s
					---
					""";
		};

		return String.format(cvUsageInstructions, cvText);
	}//getCvSectionBg


	public boolean isInterviewConcluding(String transcript) {
		if (transcript == null || transcript.isBlank()) {
			return false;
		}

		// Priority check: deterministic end signal token
		if (transcript.contains(END_SIGNAL)) {
			log.info("MATCHED end signal token [END_INTERVIEW] in AI output");
			return true;
		}

		String lowerTranscript = transcript.toLowerCase();
		log.debug("Checking conclusion patterns in transcript ({} chars)", lowerTranscript.length());

		// Check English patterns
		for (Pattern pattern : CONCLUSION_PATTERNS_EN) {
			if (pattern.matcher(transcript).find()) {
				log.info("Matched EN conclusion pattern: {}", pattern.pattern());
				return true;
			}
		}

		// Check Bulgarian patterns
		for (Pattern pattern : CONCLUSION_PATTERNS_BG) {
			if (pattern.matcher(transcript).find()) {
				log.info("Matched BG conclusion pattern: {}", pattern.pattern());
				return true;
			}
		}

		log.debug("No conclusion pattern matched");
		return false;
	}//isInterviewConcluding

}//InterviewPromptService
